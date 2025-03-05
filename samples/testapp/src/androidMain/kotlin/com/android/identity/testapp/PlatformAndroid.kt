package com.android.identity.testapp

import android.os.Build
import com.android.identity.crypto.EcCurve
import com.android.identity.context.applicationContext
import com.android.identity.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.securearea.AndroidKeystoreSecureArea
import com.android.identity.securearea.UserAuthenticationType
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaProvider
import com.android.identity.storage.Storage
import com.android.identity.storage.android.AndroidStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.multipaz.compose.notifications.NotificationManagerAndroid
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
    NotificationManagerAndroid.setSmallIcon(R.drawable.ic_stat_name)
    NotificationManagerAndroid.setChannelTitle(
        applicationContext.getString(R.string.notification_channel_title)
    )
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
        File(applicationContext.dataDir.path, "storage.db").absolutePath
    )
}

actual fun platformStorage(): Storage {
    return androidStorage
}

private val androidKeystoreSecureAreaProvider = SecureAreaProvider {
    AndroidKeystoreSecureArea.create(androidStorage)
}

actual fun platformSecureAreaProvider(): SecureAreaProvider<SecureArea> {
    return androidKeystoreSecureAreaProvider
}

actual val platformSecureAreaHasKeyAgreement by lazy {
    AndroidKeystoreSecureArea.Capabilities().keyAgreementSupported
}

actual fun platformCreateKeySettings(
    challenge: ByteString,
    curve: EcCurve,
    keyPurposes: Set<KeyPurpose>,
    userAuthenticationRequired: Boolean,
    validFrom: Instant,
    validUntil: Instant
): CreateKeySettings {
    var timeoutMillis = 0L
    // Work around Android bug where ECDH keys don't work with timeout 0, see
    // AndroidKeystoreUnlockData.cryptoObjectForKeyAgreement for details.
    if (keyPurposes.contains(KeyPurpose.AGREE_KEY)) {
        timeoutMillis = 1000L
    }
    return AndroidKeystoreCreateKeySettings.Builder(challenge.toByteArray())
        .setEcCurve(curve)
        .setKeyPurposes(keyPurposes)
        .setUserAuthenticationRequired(
            required = userAuthenticationRequired,
            timeoutMillis = timeoutMillis,
            userAuthenticationTypes = setOf(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC)
        )
        .setValidityPeriod(validFrom, validUntil)
        .build()
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
