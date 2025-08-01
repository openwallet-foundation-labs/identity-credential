package org.multipaz.storage.android

import android.database.sqlite.SQLiteDatabase
import org.multipaz.storage.Storage
import org.multipaz.storage.base.BaseStorage
import org.multipaz.storage.base.BaseStorageTable
import org.multipaz.storage.StorageTableSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.coroutines.CoroutineContext

/**
 * [Storage] implementation based on Android [SQLiteDatabase] API.
 */
class AndroidStorage: BaseStorage {
    private val coroutineContext: CoroutineContext
    private val databaseFactory: () -> SQLiteDatabase
    internal val keySize: Int
    private var database: SQLiteDatabase? = null

    constructor(
        database: SQLiteDatabase,
        clock: Clock = Clock.System,
        coroutineContext: CoroutineContext = Dispatchers.IO,
        keySize: Int = 9
    ): super(clock) {
        this.database = database
        databaseFactory = { throw IllegalStateException("unexpected call") }
        this.coroutineContext = coroutineContext
        this.keySize = keySize
    }

    constructor(
        databasePath: String?,
        clock: Clock = Clock.System,
        coroutineContext: CoroutineContext = Dispatchers.IO,
        keySize: Int = 9
    ): super(clock) {
        databaseFactory = {
            SQLiteDatabase.openOrCreateDatabase(databasePath ?: ":memory:", null)
        }
        this.coroutineContext = coroutineContext
        this.keySize = keySize
    }

    override suspend fun createTable(tableSpec: StorageTableSpec): BaseStorageTable {
        if (database == null) {
            database = databaseFactory()
        }
        val table = AndroidStorageTable(this, tableSpec)
        table.init()
        return table
    }

    internal suspend fun<T> withDatabase(
        block: suspend (database: SQLiteDatabase) -> T
    ): T {
        return withContext(coroutineContext) {
            block(database!!)
        }
    }
}