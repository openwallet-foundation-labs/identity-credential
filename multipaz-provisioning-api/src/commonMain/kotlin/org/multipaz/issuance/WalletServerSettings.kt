package org.multipaz.issuance

import org.multipaz.flow.server.Configuration
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import kotlinx.io.bytestring.ByteString

/**
 * Wallet Server settings.
 */
class WalletServerSettings(private val conf: Configuration) {

    val developerMode
        get() = getBool("developerMode", false)

    val waitForNotificationSupported
        get() = getBool("waitForNotificationSupported", false)

    val iosReleaseBuild
        get() = getBool("iosRequireReleaseBuild", false)

    val iosAppIdentifier
        get() = getString("iosRequireAppIdentifier")

    val androidRequireGmsAttestation
        get() = getBool("androidRequireGmsAttestation", true)

    val androidRequireVerifiedBootGreen
        get() = getBool("androidRequireVerifiedBootGreen", true)

    val androidRequireAppSignatureCertificateDigests: List<ByteString>
        get() = getStringList("androidRequireAppSignatureCertificateDigests").map {
                ByteString(it.fromBase64Url())
            }

    val cloudSecureAreaEnabled: Boolean
        get() = getBool("cloudSecureAreaEnabled", false)

    val cloudSecureAreaUrl: String
        get() = getString("cloudSecureAreaUrl") ?: "/csa"

    val cloudSecureAreaRekeyingIntervalSeconds: Int
        get() = getInt("cloudSecureAreaRekeyingIntervalSeconds", 300)

    val cloudSecureAreaLockoutNumFailedAttempts: Int
        get() = getInt("cloudSecureAreaLockoutNumFailedAttempts", 3)

    val cloudSecureAreaLockoutDurationSeconds: Int
        get() = getInt("cloudSecureAreaLockoutDurationSeconds", 60)

    fun getString(key: String) = conf.getValue(key)

    fun getBool(key: String, defaultValue: Boolean = false): Boolean {
        val value = conf.getValue(key)
        if (value == null) {
            Logger.d(TAG, "getBool: No value configuration value with key $key, return default value $defaultValue")
            return defaultValue
        }
        if (value == "true") {
            return true
        } else if (value == "false") {
            return false
        }
        Logger.d(TAG, "getBool: Unexpected value '$value' with key $key, return default value $defaultValue")
        return defaultValue
    }


    fun getInt(key: String, defaultValue: Int = 0): Int {
        val value = conf.getValue(key)
        if (value == null) {
            Logger.d(TAG, "getInt: No value configuration value with key $key, return default value $defaultValue")
            return defaultValue
        }
        try {
            return value.toInt()
        } catch (e: Throwable) {
            Logger.d(TAG, "getInt: Unexpected value '$value' with key $key, return default value $defaultValue")
        }
        return defaultValue
    }

    fun getStringList(key: String): List<String> {
        val value = conf.getValue(key)
        if (value == null) {
            Logger.d(TAG, "getStringList: No value configuration value with key $key")
            return emptyList()
        }
        if (value.isEmpty()) {
            return emptyList()
        }
        return value.split("\\s+".toRegex())
    }

    companion object {
        private const val TAG = "WalletServerSettings"
    }

}