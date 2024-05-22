package com.android.identity.issuance.remote

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.android.identity.flow.environment.Storage
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

    private suspend fun<T> runInExecutor(block: (database: SQLiteDatabase) -> T): T {
        return suspendCoroutine { continuation ->
            executor.submit {
                try {
                    if (database == null) {
                        database = openDatabase()
                    }
                    val result = block(database!!)
                    continuation.resume(result)
                } catch (ex: Exception) {
                    continuation.resumeWithException(ex)
                }
            }
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