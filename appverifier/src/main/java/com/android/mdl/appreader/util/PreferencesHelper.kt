package com.android.mdl.appreader.util

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.security.identity.Constants

object PreferencesHelper {
    private const val LOG_INFO = "log_info"
    private const val LOG_DEVICE_ENGAGEMENT = "log_device_engagement"
    private const val LOG_SESSION_MESSAGES = "log_session_messages"
    private const val LOG_TRANSPORT = "log_transport"
    private const val LOG_TRANSPORT_VERBOSE = "log_transport_verbose"

    fun getLoggingFlags(context: Context): Int {
        var flags = 0
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(LOG_INFO, true)) {
            flags += Constants.LOGGING_FLAG_INFO
        }
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(LOG_DEVICE_ENGAGEMENT, true)
        ) {
            flags += Constants.LOGGING_FLAG_DEVICE_ENGAGEMENT
        }
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(LOG_SESSION_MESSAGES, true)
        ) {
            flags += Constants.LOGGING_FLAG_SESSION_MESSAGES
        }
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(LOG_TRANSPORT, true)
        ) {
            flags += Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC
        }
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(LOG_TRANSPORT_VERBOSE, true)
        ) {
            flags += Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE
        }
        return flags
    }
}