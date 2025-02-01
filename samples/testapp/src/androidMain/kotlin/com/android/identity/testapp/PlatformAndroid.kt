package com.android.identity.testapp

import android.os.Build
import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureArea
import com.android.identity.util.AndroidContexts
import com.android.identity.securearea.SecureAreaProvider
import com.android.identity.storage.Storage
import com.android.identity.storage.android.AndroidStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.net.NetworkInterface
import java.security.Security

actual val platform = Platform.ANDROID

private var platformInitialized = false
private val platformInitLock = Mutex()

actual suspend fun platformInit() {
    platformInitLock.withLock {
        if (platformInitialized) {
            return
        }
        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        platformInitialized = true
    }
}

actual fun getLocalIpAddress(): String {
    for (iface in NetworkInterface.getNetworkInterfaces()) {
        for (inetAddress in iface.inetAddresses) {
            if (!inetAddress.isLoopbackAddress) {
                val address = inetAddress.hostAddress
                if (address != null && address.indexOf(':') < 0) {
                    return address
                }
            }
        }
    }
    throw IllegalStateException("Unable to determine address")
}

private val androidStorage: AndroidStorage by lazy {
    AndroidStorage(
        File(AndroidContexts.applicationContext.dataDir.path, "storage.db").absolutePath
    )
}

actual fun platformStorage(): Storage {
    return androidStorage
}

private val androidKeystoreSecureAreaProvider = SecureAreaProvider {
    AndroidKeystoreSecureArea.create(AndroidContexts.applicationContext, androidStorage)
}

actual fun platformSecureAreaProvider(): SecureAreaProvider<SecureArea> {
    return androidKeystoreSecureAreaProvider
}

actual fun platformKeySetting(clientId: String): CreateKeySettings {
    return AndroidKeystoreCreateKeySettings.Builder(clientId.toByteArray()).build()
}

// https://stackoverflow.com/a/21505193/878126
actual val platformIsEmulator: Boolean by lazy {
    // Android SDK emulator
    return@lazy ((Build.MANUFACTURER == "Google" && Build.BRAND == "google" &&
            ((Build.FINGERPRINT.startsWith("google/sdk_gphone_")
                    && Build.FINGERPRINT.endsWith(":user/release-keys")
                    && Build.PRODUCT.startsWith("sdk_gphone_")
                    && Build.MODEL.startsWith("sdk_gphone_"))
                    //alternative
                    || (Build.FINGERPRINT.startsWith("google/sdk_gphone64_")
                    && (Build.FINGERPRINT.endsWith(":userdebug/dev-keys") || Build.FINGERPRINT.endsWith(
                ":user/release-keys"
            ))
                    && Build.PRODUCT.startsWith("sdk_gphone64_")
                    && Build.MODEL.startsWith("sdk_gphone64_"))))
            //
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            //bluestacks
            || "QC_Reference_Phone" == Build.BOARD && !"Xiaomi".equals(
        Build.MANUFACTURER,
        ignoreCase = true
    )
            //bluestacks
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.HOST.startsWith("Build")
            //MSI App Player
            || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
            || Build.PRODUCT == "google_sdk")
    // another Android SDK emulator check
    /* || SystemProperties.getProp("ro.kernel.qemu") == "1") */
}
