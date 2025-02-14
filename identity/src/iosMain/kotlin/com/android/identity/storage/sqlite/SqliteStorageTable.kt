package com.android.identity.storage.sqlite

import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import androidx.sqlite.use
import com.android.identity.storage.KeyExistsStorageException
import com.android.identity.storage.NoRecordStorageException
import com.android.identity.storage.base.BaseStorageTable
import com.android.identity.storage.StorageTableSpec
import com.android.identity.storage.base.SqlStatementMaker
import com.android.identity.util.toBase64Url
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import kotlin.random.Random

class SqliteStorageTable(
    override val storage: SqliteStorage,
    spec: StorageTableSpec
): BaseStorageTable(spec) {
    private var sql = SqlStatementMaker(
        spec = spec,
        textType = "TEXT",
        blobType = "BLOB",
        longType = "INTEGER",
        // NB: SQLiteConnection API without RETURNING clause support lacks a way to faithfully
        // implement some operations atomically. RETURNING clause is not currently supported
        // when using Android (non-bundled) driver, so using this implementation on Android would
        // require bundled driver. AndroidStorage implementation (which uses non-multiplatform
        // sqlite APIs) is probably a better alternative.
        useReturningClause = true,
        collationCharset = null
    )

    internal suspend fun init() {
        storage.withConnection { connection ->
            connection.execSQL(sql.createTableStatement)
        }
    }

    override suspend fun get(key: String, partitionId: String?): ByteString? {
        checkPartition(partitionId)
        return storage.withConnection { connection ->
            connection.prepare(sql.getStatement).use { statement ->
                statement.bindText(1, key)
                var index = 2
                if (spec.supportPartitions) {
                    statement.bindText(index++, partitionId!!)
                }
                if (spec.supportExpiration) {
                    statement.bindLong(index, storage.clock.now().epochSeconds)
                }
                if (statement.step()) {
                    ByteString(statement.getBlob(0))
                } else {
                    null
                }
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
        return storage.withConnection { connection ->
            var newKey: String
            if (key != null && spec.supportExpiration) {
                // if there is an entry with this key, but it is expired, it needs to be purged.
                // Purging expired keys does not interfere with operation atomicity
                connection.prepare(sql.purgeExpiredWithIdStatement).use { statement ->
                    statement.bindText(1, key)
                    var index = 2
                    if (spec.supportPartitions) {
                        statement.bindText(index++, partitionId!!)
                    }
                    statement.bindLong(index, storage.clock.now().epochSeconds)
                    statement.step()
                }
            }
            var done = false
            // Loop until the key we generated is unique (if key is not null, we only loop once).
            do {
                newKey = key ?: Random.nextBytes(storage.keySize).toBase64Url()
                connection.prepare(sql.insertStatement).use { statement ->
                    var index = 1
                    if (spec.supportPartitions) {
                        statement.bindText(index++, partitionId!!)
                    }
                    statement.bindText(index++, newKey)
                    if (spec.supportExpiration) {
                        statement.bindLong(index++, expiration.epochSeconds)
                    }
                    statement.bindBlob(index, data.toByteArray())
                    try {
                        // This statement will throw an exception if the key already exists.
                        // Unfortunately, different implementations throw different exceptions.
                        // When key is null (i.e. a unique one must be generated), retry, otherwise,
                        // fail.
                        statement.step()
                        done = true
                    } catch (err: SQLiteException) {  /* apple */
                        // nothing
                    } catch (err: RuntimeException) {
                        val errorName = err::class.simpleName
                        if (errorName != "SQLiteConstraintException" /* android */
                            && errorName != "SQLException" /* bundled */
                            ) {
                            throw err
                        }
                    }
                    if (!done && key != null) {
                        throw KeyExistsStorageException(
                            "Record with ${recordDescription(key, partitionId)} already exists"
                        )
                    }
                }
            } while(!done)
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
        storage.withConnection { connection ->
            val nowSeconds = storage.clock.now().epochSeconds
            val committed = connection.prepare(
                if (expiration == null) {
                    sql.updateStatement
                } else {
                    sql.updateWithExpirationStatement
                }
            ).use { statement ->
                statement.bindBlob(1, data.toByteArray())
                var index = 2
                if (expiration != null) {
                    statement.bindLong(index++, expiration.epochSeconds)
                }
                statement.bindText(index++, key)
                if (spec.supportPartitions) {
                    statement.bindText(index++, partitionId!!)
                }
                if (spec.supportExpiration) {
                    statement.bindLong(index, nowSeconds)
                }
                statement.step()
            }
            if (!committed) {
                throw NoRecordStorageException(
                    "No record with ${recordDescription(key, partitionId)}")
            }
        }
    }

    override suspend fun delete(key: String, partitionId: String?): Boolean {
        checkPartition(partitionId)
        return storage.withConnection { connection ->
            val nowSeconds = storage.clock.now().epochSeconds
            connection.prepare(sql.deleteStatement).use { statement ->
                statement.bindText(1, key)
                var index = 2
                if (spec.supportPartitions) {
                    statement.bindText(index++, partitionId!!)
                }
                if (spec.supportExpiration) {
                    statement.bindLong(index, nowSeconds)
                }
                statement.step()
            }
        }
    }

    override suspend fun deleteAll() {
        return storage.withConnection { connection ->
            connection.prepare(sql.deleteAllStatement).use { statement ->
                statement.step()
            }
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
        return storage.withConnection { connection ->
            connection.prepare(
                if (limit < Int.MAX_VALUE) {
                    sql.enumerateWithLimitStatement
                } else {
                    sql.enumerateStatement
                }
            ).use { statement ->
                var index = 1
                statement.bindText(index++, afterKey ?: "")
                if (spec.supportPartitions) {
                    statement.bindText(index++, partitionId!!)
                }
                if (spec.supportExpiration) {
                    statement.bindLong(index++, storage.clock.now().epochSeconds)
                }
                if (limit < Int.MAX_VALUE) {
                    statement.bindInt(index, limit)
                }
                val list = mutableListOf<String>()
                while (statement.step()) {
                    list.add(statement.getText(0))
                }
                list
            }
        }
    }

    override suspend fun purgeExpired() {
        return storage.withConnection { connection ->
            connection.prepare(sql.purgeExpiredStatement).use { statement ->
                statement.bindLong(1, storage.clock.now().epochSeconds)
                statement.step()
            }
        }
    }
}