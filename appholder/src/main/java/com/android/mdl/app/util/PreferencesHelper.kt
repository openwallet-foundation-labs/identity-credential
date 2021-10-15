package com.android.mdl.app.util

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.security.identity.Constants

object PreferencesHelper {
    const val HARDWARE_BACKED_PREFERENCE = "com.android.mdl.app.HARDWARE_BACKED"
    const val BLE_DATA_RETRIEVAL = "ble_transport"
    const val WIFI_DATA_RETRIEVAL = "wifi_transport"
    const val NFC_DATA_RETRIEVAL = "nfc_transport"
    const val BLE_OPTION = "ble_option"
    private const val LOG_INFO = "log_info"
    private const val LOG_DEVICE_ENGAGEMENT = "log_device_engagement"
    private const val LOG_SESSION_MESSAGES = "log_session_messages"
    private const val LOG_TRANSPORT = "log_transport"
    private const val LOG_TRANSPORT_VERBOSE = "log_transport_verbose"
    const val USE_READER_AUTH = "use_reader_authentication"

    fun setHardwareBacked(context: Context, isHardwareBacked: Boolean) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!hasHardwareBackedPreference(context)) {
            sharedPreferences.edit().putBoolean(
                HARDWARE_BACKED_PREFERENCE,
                isHardwareBacked
            ).apply()
        }
    }

    fun hasHardwareBackedPreference(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).contains(HARDWARE_BACKED_PREFERENCE)

    fun isHardwareBacked(context: Context): Boolean {
        if (!hasHardwareBackedPreference(context)) {
            throw IllegalStateException("No preference set for used implementation.")
        }
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            HARDWARE_BACKED_PREFERENCE, false
        )
    }

    // Default value for BLE data retrieval is true
    fun isBleDataRetrievalEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            BLE_DATA_RETRIEVAL, true
        )
    }

    fun getBleOption(context: Context): Int {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(BLE_OPTION, "2")
            ?.toInt() ?: 2
    }

    fun isWifiDataRetrievalEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            WIFI_DATA_RETRIEVAL, false
        )
    }

    fun isNfcDataRetrievalEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            NFC_DATA_RETRIEVAL, false
        )
    }

    fun getLoggingFlags(context: Context): Int {
        var flags = 0
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(LOG_INFO, false)) {
            flags += Constants.LOGGING_FLAG_INFO
        }
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(LOG_DEVICE_ENGAGEMENT, false)
        ) {
            flags += Constants.LOGGING_FLAG_DEVICE_ENGAGEMENT
        }
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(LOG_SESSION_MESSAGES, false)
        ) {
            flags += Constants.LOGGING_FLAG_SESSION_MESSAGES
        }
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(LOG_TRANSPORT, false)
        ) {
            flags += Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC
        }
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(LOG_TRANSPORT_VERBOSE, false)
        ) {
            flags += Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE
        }
        return flags
    }

    fun isReaderAuthenticationEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            USE_READER_AUTH, false
        )
    }
}