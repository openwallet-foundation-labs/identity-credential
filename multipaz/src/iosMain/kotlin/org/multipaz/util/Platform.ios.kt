package org.multipaz.util

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.NativeSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureEnclaveSecureArea
import org.multipaz.storage.Storage
import org.multipaz.storage.sqlite.SqliteStorage
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.UIKit.UIDevice

@OptIn(ExperimentalForeignApi::class)
private fun openDatabase(filename: String, setExcludedFromBackupFlag: Boolean): SQLiteConnection {
    val fileManager = NSFileManager.defaultManager
    val rootPath = fileManager.URLForDirectory(
        NSDocumentDirectory,
        NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null)
        ?: throw RuntimeException("could not get documents directory url")
    println("Root path: $rootPath")
    val connection = NativeSQLiteDriver().open(rootPath.path() + "/$filename")
    if (setExcludedFromBackupFlag) {
        rootPath.setResourceValue(
            value = true,
            forKey = NSURLIsExcludedFromBackupKey,
            error = null,
        )
    }
    return connection
}

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
actual object Platform {
    actual val name = "${UIDevice.currentDevice.systemName()} ${UIDevice.currentDevice.systemVersion}"

    actual suspend fun getStorage(): Storage {
        return SqliteStorage(
            connection = openDatabase(
                filename = "storage.db",
                setExcludedFromBackupFlag = false
            ),
            // native sqlite crashes when used with Dispatchers.IO
            coroutineContext = newSingleThreadContext("DB")
        )
    }

    actual suspend fun getNonBackedUpStorage(): Storage {
        return SqliteStorage(
            connection = openDatabase(
                filename = "storageNoBackup.db",
                setExcludedFromBackupFlag = true
            ),
            // native sqlite crashes when used with Dispatchers.IO
            coroutineContext = newSingleThreadContext("DB")
        )
    }

    actual suspend fun getSecureArea(storage: Storage): SecureArea {
        return SecureEnclaveSecureArea.create(storage)
    }
}
