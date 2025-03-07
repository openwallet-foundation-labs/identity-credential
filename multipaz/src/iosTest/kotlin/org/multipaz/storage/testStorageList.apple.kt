package org.multipaz.storage

import androidx.sqlite.driver.NativeSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.storage.sqlite.SqliteStorage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.datetime.Clock
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
actual fun createTransientStorageList(testClock: Clock): List<Storage> {
    val bundledDb = BundledSQLiteDriver().open(":memory:")
    val nativeDb = NativeSQLiteDriver().open(":memory:")
    return listOf(
        EphemeralStorage(testClock),
        SqliteStorage(
            connection = nativeDb,
            clock = testClock,
            // native sqlite crashes when used with Dispatchers.IO
            coroutineContext = newSingleThreadContext("DB")
        ),
        SqliteStorage(
            connection = bundledDb,
            clock = testClock,
            // bundled sqlite crashes when used with Dispatchers.IO
            coroutineContext = newSingleThreadContext("DB")
        )
    )
}

val knownNames = mutableSetOf<String>()

@OptIn(
    ExperimentalCoroutinesApi::class,
    DelicateCoroutinesApi::class,
    ExperimentalForeignApi::class
)
actual fun createPersistentStorage(name: String, testClock: Clock): Storage? {
    val paths = NSSearchPathForDirectoriesInDomains(
        directory = NSCachesDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true
    )
    if (paths.isEmpty()) {
        throw IllegalStateException("No caches directory")
    }
    val dbPath = "${paths[0]}/$name.db"
    if (knownNames.add(name)) {
        if (NSFileManager.defaultManager.fileExistsAtPath(dbPath)) {
            NSFileManager.defaultManager.removeItemAtPath(dbPath, null)
        }
    }
    return SqliteStorage(
        connection = NativeSQLiteDriver().open(dbPath),
        clock = testClock,
        // native sqlite crashes when used with Dispatchers.IO
        coroutineContext = newSingleThreadContext("DB")
    )
}