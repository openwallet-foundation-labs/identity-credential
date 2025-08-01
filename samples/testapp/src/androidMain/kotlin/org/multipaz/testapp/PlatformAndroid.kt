package org.multipaz.testapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.jakewharton.processphoenix.ProcessPhoenix
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.android.Android
import org.multipaz.context.applicationContext
import org.multipaz.securearea.AndroidKeystoreCreateKeySettings
import org.multipaz.securearea.AndroidKeystoreSecureArea
import org.multipaz.securearea.UserAuthenticationType
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaProvider
import org.multipaz.storage.Storage
import org.multipaz.storage.android.AndroidStorage
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import multipazproject.samples.testapp.generated.resources.Res
import multipazproject.samples.testapp.generated.resources.app_icon
import multipazproject.samples.testapp.generated.resources.app_icon_red
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.multipaz.compose.notifications.NotificationManagerAndroid
import org.multipaz.context.getActivity
import org.multipaz.crypto.Algorithm
import org.multipaz.prompt.AndroidPromptModel
import org.multipaz.prompt.PromptModel
import org.multipaz.util.Logger
import java.io.File
import java.net.NetworkInterface
import java.security.Security

private const val TAG = "PlatformAndroid"

actual val platformAppName = applicationContext.getString(R.string.app_name)

actual val platformAppIcon = if (platformAppName.endsWith("(Red)")) {
    Res.drawable.app_icon_red
} else {
    Res.drawable.app_icon
}

actual val platformPromptModel: PromptModel by lazy {
    AndroidPromptModel()
}

actual val platform = Platform.ANDROID

actual suspend fun platformInit() {
    NotificationManagerAndroid.setSmallIcon(R.drawable.ic_stat_name)
    NotificationManagerAndroid.setChannelTitle(
        applicationContext.getString(R.string.notification_channel_title)
    )
}

actual suspend fun platformCryptoInit(settingsModel: TestAppSettingsModel) {
    if (settingsModel.cryptoPreferBouncyCastle.value) {
        Logger.i(TAG, "Forcing BouncyCastle to the top of the list")
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}

actual fun platformRestartApp() {
    ProcessPhoenix.triggerRebirth(applicationContext)
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

actual fun platformHttpClientEngineFactory(): HttpClientEngineFactory<*> = Android

actual val platformSecureAreaHasKeyAgreement by lazy {
    AndroidKeystoreSecureArea.Capabilities().keyAgreementSupported
}

actual fun platformCreateKeySettings(
    challenge: ByteString,
    algorithm: Algorithm,
    userAuthenticationRequired: Boolean,
    validFrom: Instant,
    validUntil: Instant
): CreateKeySettings {
    check(algorithm.fullySpecified)
    var timeoutMillis = 0L
    // Work around Android bug where ECDH keys don't work with timeout 0, see
    // AndroidKeystoreUnlockData.cryptoObjectForKeyAgreement for details...
    //
    // Also, on some devices (not all!) using both face and fingerprint the time
    // spent in the biometric prompt counts against the timeout... so if we use a low
    // value like one second it'll cause a second biometric prompt to appear. Work
    // around this bug by using a high timeout.
    //
    if (algorithm.isKeyAgreement) {
        timeoutMillis = 15_000L
    }
    return AndroidKeystoreCreateKeySettings.Builder(challenge)
        .setAlgorithm(algorithm)
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
