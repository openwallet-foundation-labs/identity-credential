package com.android.mdl.appreader.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.android.identity.Constants

object PreferencesHelper {
    private const val BLE_DATA_L2CAP = "ble_l2cap"
    private const val BLE_CLEAR_CACHE = "ble_clear_cache"
    private const val READER_AUTHENTICATION = "reader_authentication"
    private const val LOG_INFO = "log_info"
    private const val LOG_DEVICE_ENGAGEMENT = "log_device_engagement"
    private const val LOG_SESSION_MESSAGES = "log_session_messages"
    private const val LOG_TRANSPORT = "log_transport"
    private const val LOG_TRANSPORT_VERBOSE = "log_transport_verbose"

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

    fun getReaderAuth(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(READER_AUTHENTICATION, "0") ?: "0"
    }
}