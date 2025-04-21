package org.multipaz.securearea.cloud

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.NativeSQLiteDriver
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureEnclaveCreateKeySettings
import org.multipaz.securearea.SecureEnclaveSecureArea
import org.multipaz.securearea.SecureEnclaveUserAuthType
import org.multipaz.storage.Storage
import org.multipaz.storage.sqlite.SqliteStorage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.Algorithm
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun openDatabase(): SQLiteConnection {
    val fileManager = NSFileManager.defaultManager
    val rootPath = fileManager.URLForDirectory(
        NSDocumentDirectory,
        NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null)
        ?: throw RuntimeException("could not get documents directory url")
    println("Root path: $rootPath")
    return NativeSQLiteDriver().open(rootPath.path() + "/storage.db")
}

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
private val iosStorage = SqliteStorage(
    connection = openDatabase(),
    // native sqlite crashes when used with Dispatchers.IO
    coroutineContext = newSingleThreadContext("DB")
)

internal actual suspend fun cloudSecureAreaGetPlatformSecureArea(
    storage: Storage,
    partitionId: String,
): SecureArea {
    return SecureEnclaveSecureArea.create(iosStorage, "_csa_$partitionId")
}

internal actual fun cloudSecureAreaGetPlatformSecureAreaCreateKeySettings(
    challenge: ByteString,
    algorithm: Algorithm,
    userAuthenticationRequired: Boolean,
    userAuthenticationTypes: Set<CloudUserAuthType>
): CreateKeySettings {
    val secureEnclaveUserAuthTypes = when (userAuthenticationTypes) {
        setOf<CloudUserAuthType>() -> setOf<SecureEnclaveUserAuthType>()

        setOf<CloudUserAuthType>(
            CloudUserAuthType.PASSCODE,
        ) -> setOf<SecureEnclaveUserAuthType>(
            SecureEnclaveUserAuthType.DEVICE_PASSCODE
        )

        setOf<CloudUserAuthType>(
            CloudUserAuthType.BIOMETRIC,
        ) -> setOf<SecureEnclaveUserAuthType>(SecureEnclaveUserAuthType.BIOMETRY_CURRENT_SET)

        setOf<CloudUserAuthType>(
            CloudUserAuthType.PASSCODE,
            CloudUserAuthType.BIOMETRIC,
        ) -> setOf<SecureEnclaveUserAuthType>(SecureEnclaveUserAuthType.USER_PRESENCE)

        else -> throw IllegalStateException("Unexpected userAuthenticationTypes $userAuthenticationTypes")
    }

    return SecureEnclaveCreateKeySettings.Builder()
        .setAlgorithm(algorithm)
        .setUserAuthenticationRequired(
            required = userAuthenticationRequired,
            userAuthenticationTypes = secureEnclaveUserAuthTypes
        )
        .build()
}
