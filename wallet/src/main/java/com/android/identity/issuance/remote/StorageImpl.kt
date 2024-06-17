package com.android.identity.issuance.remote

import android.content.ContentValues
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import com.android.identity.flow.server.Storage
import com.android.identity.util.Logger
import kotlinx.io.bytestring.ByteString
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class StorageImpl(
    private val context: Context,
    private val databaseName: String
) : Storage {
    private var database: SQLiteDatabase? = null
    private val executor = Executors.newSingleThreadExecutor()!!
    private val createdTables = mutableSetOf<String>()

    companion object {
        const val TAG = "StorageImpl"
    }

    private suspend fun<T> runInExecutor(block: (database: SQLiteDatabase) -> T): T {
        return suspendCoroutine { continuation ->
            Logger.i(TAG, "Submitting database task")
            executor.submit {
                Logger.i(TAG, "Database task started")
                try {
                    if (database == null) {
                        database = openDatabase()
                    }
                    val result = block(database!!)
                    Logger.i(TAG, "Database task finished")
                    continuation.resume(result)
                } catch (ex: Throwable) {
                    Logger.e(TAG, "Database task error", ex)
                    continuation.resumeWithException(ex)
                }
            }
            Logger.i(TAG, "Database task submitted")
        }
    }

    override suspend fun get(table: String, peerId: String, key: String): ByteString? {
        val safeTable = sanitizeTable(table)
        return runInExecutor { database ->
            ensureTable(safeTable)
            val cursor = database.query(
                safeTable,
                arrayOf("data"),
                "peerId = ? AND key = ?",
                arrayOf(peerId, key),
                null,
                null,
                null
            )
            // TODO: Older OS versions don't support setting the cursor window size.
            //  What should we do with older OS versions?
            //  Also note that a large window size may lead to longer delays when loading from the
            //  database. And if we keep this, replace the magic number with a constant.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // The default window size of 2MB is too small for video files.
                (cursor as? AbstractWindowedCursor)?.window = CursorWindow(
                    "Larger Window", 256 * 1024 * 1024)
            }
            if (cursor.moveToFirst()) {
                val bytes = cursor.getBlob(0)
                cursor.close()
                ByteString(bytes)
            } else {
                null
            }
        }
    }

    override suspend fun insert(table: String, peerId: String, data: ByteString, key: String): String {
        val safeTable = sanitizeTable(table)
        return runInExecutor { database ->
            val recordKey = key.ifEmpty { UUID.randomUUID().toString() }
            ensureTable(safeTable)
            val rowId = database.insert(safeTable, null, ContentValues().apply {
                put("key", recordKey)
                put("peerId", peerId)
                put("data", data.toByteArray())
            })
            if (rowId < 0) {
                throw IllegalStateException("Value was not inserted")
            }
            recordKey
        }
    }

    override suspend fun update(table: String, peerId: String, key: String, data: ByteString) {
        val safeTable = sanitizeTable(table)
        return runInExecutor { database ->
            ensureTable(safeTable)
            val count = database.update(
                safeTable,
                ContentValues().apply {
                    put("data", data.toByteArray())
                },
                "peerId = ? AND key = ?",
                arrayOf(peerId, key)
            )
            if (count != 1) {
                throw IllegalStateException("Value was not updated")
            }
        }
    }

    override suspend fun delete(table: String, peerId:String, key: String): Boolean {
        val safeTable = sanitizeTable(table)
        return runInExecutor { database ->
            ensureTable(safeTable)
            val count = database.delete(safeTable, "peerId = ? AND key = ?",
                arrayOf(peerId, key))
            count > 0
        }
    }

    override suspend fun enumerate(table: String, peerId: String,
                                   notBeforeKey: String, limit: Int): List<String> {
        val safeTable = sanitizeTable(table)
        return runInExecutor { database ->
            ensureTable(safeTable)
            val cursor = database.query(
                safeTable,
                arrayOf("key"),
                "peerId = ? AND key > ?",
                arrayOf(peerId, notBeforeKey),
                null,
                null,
                "key",
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

    private fun ensureTable(safeTable: String) {
        if (!createdTables.contains(safeTable)) {
            database!!.execSQL("""
                CREATE TABLE IF NOT EXISTS $safeTable (
                    key TEXT PRIMARY KEY,
                    peerId TEXT,
                    data BLOB
                )
            """.trimIndent())
            createdTables.add(safeTable)
        }
    }

    private fun openDatabase(): SQLiteDatabase {
        val file = context.getDatabasePath(databaseName)
        val params = SQLiteDatabase.OpenParams.Builder()
            .setOpenFlags(SQLiteDatabase.CREATE_IF_NECESSARY)
            .build()
        return SQLiteDatabase.openDatabase(file, params);
    }

    private fun sanitizeTable(table: String): String {
        return "St_$table"
    }
}