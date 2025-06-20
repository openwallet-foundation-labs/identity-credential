package org.multipaz.csa.server

import kotlinx.io.bytestring.ByteString
import org.multipaz.rpc.backend.Configuration
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url

class CloudSecureAreaSettings(private val conf: Configuration) {

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

    val cloudSecureAreaRekeyingIntervalSeconds: Int
        get() = getInt("cloudSecureAreaRekeyingIntervalSeconds", 300)

    val cloudSecureAreaLockoutNumFailedAttempts: Int
        get() = getInt("cloudSecureAreaLockoutNumFailedAttempts", 3)

    val cloudSecureAreaLockoutDurationSeconds: Int
        get() = getInt("cloudSecureAreaLockoutDurationSeconds", 60)

    private fun getString(key: String) = conf.getValue(key)

    private fun getBool(key: String, defaultValue: Boolean = false): Boolean {
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

    private fun getInt(key: String, defaultValue: Int = 0): Int {
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
        private const val TAG = "CloudSecureAreaSettings"
    }
}
