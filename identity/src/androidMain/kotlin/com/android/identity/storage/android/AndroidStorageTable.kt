package com.android.identity.storage.android

import android.content.ContentValues
import android.database.AbstractWindowedCursor
import android.database.CursorWindow
import android.os.Build
import com.android.identity.storage.KeyExistsStorageException
import com.android.identity.storage.NoRecordStorageException
import com.android.identity.storage.base.BaseStorageTable
import com.android.identity.storage.StorageTableSpec
import com.android.identity.storage.base.SqlStatementMaker
import com.android.identity.util.toBase64Url
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import kotlin.random.Random

internal class AndroidStorageTable(
    private val owner: AndroidStorage,
    spec: StorageTableSpec
): BaseStorageTable(spec) {
    private val sql = SqlStatementMaker(
        spec,
        textType = "TEXT",
        blobType = "BLOB",
        longType = "INTEGER",
        useReturningClause = false,
        collationCharset = null
    )

    suspend fun init() {
        owner.withDatabase { database ->
            database.execSQL(sql.createTableStatement)
        }
    }

    override suspend fun get(key: String, partitionId: String?): ByteString? {
        checkPartition(partitionId)
        return owner.withDatabase { database ->
            val cursor = database.query(
                sql.tableName,
                arrayOf("data"),
                sql.conditionWithExpiration(owner.clock.now().epochSeconds),
                whereArgs(key, partitionId),
                null,
                null,
                null
            )
            // TODO: Older OS versions don't support setting the cursor window size.
            //  What should we do with older OS versions?
            //  Also note that a large window size may lead to longer delays when loading from the
            //  database.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // The default window size of 2MB which is not the limit we want to be
                // constrained by.
                (cursor as? AbstractWindowedCursor)?.window = CursorWindow(
                    "Larger Window", CURSOR_WINDOW_SIZE)
            }
            if (cursor.moveToFirst()) {
                val bytes = cursor.getBlob(0)
                cursor.close()
                ByteString(bytes)
            } else {
                cursor.close()
                null
            }
        }
    }

    override suspend fun insert(
        key: String?,
        data: ByteString,
        partitionId: String?,
        expiration: Instant
    ): String {
        if (key != null) {
            checkKey(key)
        }
        checkPartition(partitionId)
        checkExpiration(expiration)
        return owner.withDatabase { database ->
            if (key != null && spec.supportExpiration) {
                // if there is an entry with this key, but it is expired, it needs to be purged.
                // Purging expired keys does not interfere with operation atomicity
                database.delete(
                    sql.tableName,
                    sql.purgeExpiredWithIdCondition(owner.clock.now().epochSeconds),
                    whereArgs(key, partitionId)
                )
            }
            var newKey: String
            var done = false
            do {
                newKey = key ?: Random.nextBytes(owner.keySize).toBase64Url()
                val values = ContentValues().apply {
                    put("id", newKey)
                    if (spec.supportPartitions) {
                        put("partitionId", partitionId)
                    }
                    if (spec.supportExpiration) {
                        put("expiration", expiration.epochSeconds)
                    }
                    put("data", data.toByteArray())
                }
                val rowId = database.insert(sql.tableName, null, values)
                if (rowId >= 0) {
                    done = true
                } else if (key != null) {
                    throw KeyExistsStorageException(
                        "Record with ${recordDescription(key, partitionId)} already exists")
                }
            } while (!done)
            newKey
        }
    }

    override suspend fun update(
        key: String,
        data: ByteString,
        partitionId: String?,
        expiration: Instant?
    ) {
        checkPartition(partitionId)
        if (expiration != null) {
            checkExpiration(expiration)
        }
        owner.withDatabase { database ->
            val nowSeconds = owner.clock.now().epochSeconds
            val values = ContentValues().apply {
                if (expiration != null) {
                    put("expiration", expiration.epochSeconds)
                }
                put("data", data.toByteArray())
            }
            val count = database.update(
                sql.tableName,
                values,
                sql.conditionWithExpiration(nowSeconds),
                whereArgs(key, partitionId)
            )
            if (count != 1) {
                throw NoRecordStorageException(
                    "No record with ${recordDescription(key, partitionId)}")
            }
        }
    }

    override suspend fun delete(key: String, partitionId: String?): Boolean {
        checkPartition(partitionId)
        return owner.withDatabase { database ->
            val nowSeconds = owner.clock.now().epochSeconds
            val count = database.delete(
                sql.tableName,
                sql.conditionWithExpiration(nowSeconds),
                whereArgs(key, partitionId)
            )
            count > 0
        }
    }

    override suspend fun deleteAll() {
        owner.withDatabase { database ->
            database.execSQL(sql.deleteAllStatement)
        }
    }

    override suspend fun enumerate(
        partitionId: String?,
        afterKey: String?,
        limit: Int
    ): List<String> {
        checkPartition(partitionId)
        checkLimit(limit)
        if (limit == 0) {
            return listOf()
        }
        return owner.withDatabase { database ->
            val cursor = database.query(
                sql.tableName,
                arrayOf("id"),
                sql.enumerateConditionWithExpiration(owner.clock.now().epochSeconds),
                whereArgs(afterKey ?: "", partitionId),
                null,
                null,
                "id",
                if (limit < Int.MAX_VALUE) "0, $limit" else null
            )
            val list = mutableListOf<String>()
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0))
            }
            cursor.close()
            list
        }
    }

    override suspend fun purgeExpired() {
        owner.withDatabase { database ->
            database.execSQL(sql.purgeExpiredStatement
                .replace("?", owner.clock.now().epochSeconds.toString()))
        }
    }

    private fun whereArgs(key: String, partitionId: String?): Array<String> {
        return if (spec.supportPartitions) {
            arrayOf(key, partitionId!!)
        } else {
            arrayOf(key)
        }
    }

    companion object {
        const val CURSOR_WINDOW_SIZE = 5 * 1024 * 1024L
    }
}