package com.android.identity.issuance

import com.android.identity.flow.server.Configuration
import com.android.identity.util.Logger

/**
 * Wallet Server settings.
 */
class WalletServerSettings(private val conf: Configuration) {

    val developerMode
        get() = getBool("developerMode", false)

    val waitForNotificationSupported
        get() = getBool("waitForNotificationSupported", false)

    val androidRequireGmsAttestation
        get() = getBool("androidRequireGmsAttestation", true)

    val androidRequireVerifiedBootGreen
        get() = getBool("androidRequireVerifiedBootGreen", true)

    val androidRequireAppSignatureCertificateDigests: List<String>
        get() = getStringList("androidRequireAppSignatureCertificateDigests")

    val databaseConnection: String?
        get() = getString("databaseConnection")

    val databaseUser: String?
        get() = getString("databaseUser")

    val databasePassword: String?
        get() = getString("databasePassword")

    fun getString(key: String) = conf.getValue(key)

    fun getBool(name: String, defaultValue: Boolean = false): Boolean {
        val value = conf.getValue(name)
        if (value == null) {
            Logger.d(TAG, "getBool: No value configuration value with key $name, return default value $defaultValue")
            return defaultValue
        }
        if (value == "true") {
            return true
        } else if (value == "false") {
            return false
        }
        Logger.d(TAG, "getBool: Unexpected value '$value' with key $name, return default value $defaultValue")
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