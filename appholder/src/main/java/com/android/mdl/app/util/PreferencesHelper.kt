package com.android.mdl.app.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.android.identity.Constants
import java.io.File

object PreferencesHelper {
    const val HARDWARE_BACKED_PREFERENCE = "com.android.mdl.app.HARDWARE_BACKED"
    const val BLE_DATA_RETRIEVAL = "ble_transport"
    const val BLE_DATA_RETRIEVAL_PERIPHERAL_MODE = "ble_transport_peripheral_mode"
    const val BLE_DATA_L2CAP = "ble_l2cap"
    const val BLE_CLEAR_CACHE = "ble_clear_cache"
    const val WIFI_DATA_RETRIEVAL = "wifi_transport"
    const val NFC_DATA_RETRIEVAL = "nfc_transport"
    private const val LOG_INFO = "log_info"
    private const val LOG_DEVICE_ENGAGEMENT = "log_device_engagement"
    private const val LOG_SESSION_MESSAGES = "log_session_messages"
    private const val LOG_TRANSPORT = "log_transport"
    private const val LOG_TRANSPORT_VERBOSE = "log_transport_verbose"
//    const val USE_READER_AUTH = "use_reader_authentication"

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

    fun getKeystoreBackedStorageLocation(context: Context): File {
        // As per the docs, the credential data contains reference to Keystore aliases so ensure
        // this is stored in a location where it's not automatically backed up and restored by
        // Android Backup as per https://developer.android.com/guide/topics/data/autobackup
        return context.noBackupFilesDir
    }

    // Default value for BLE data retrieval is true
    fun isBleDataRetrievalEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            BLE_DATA_RETRIEVAL, true
        )
    }

    fun isBleDataRetrievalPeripheralModeEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            BLE_DATA_RETRIEVAL_PERIPHERAL_MODE, false
        )
    }

    fun isBleL2capEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            BLE_DATA_L2CAP, false
        )
    }

    fun isBleClearCacheEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            BLE_CLEAR_CACHE, false
        )
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

    fun isReaderAuthenticationEnabled(context: Context): Boolean {
//        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
//            USE_READER_AUTH, false
//        )
        // Just returning false now as we are not using ACP to control the reader authentication
        return false
    }
}