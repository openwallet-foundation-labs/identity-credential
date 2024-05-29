package com.android.identity_credential.wallet

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.util.Logger
import com.android.identity_credential.mrtd.mrtdSetLogger
import java.io.File

class SettingsModel(
    private val walletApplication: WalletApplication,
    private val sharedPreferences: SharedPreferences
) {
    // Settings that are visible in the Settings screen
    val developerModeEnabled = MutableLiveData(false)
    val loggingEnabled = MutableLiveData(false)
    val walletServerUrl = MutableLiveData<String>(DEFAULT_WALLET_SERVER_URL)

    // Non visible in the Settings screen
    val focusedCardId = MutableLiveData("")
    val hideMissingProximityPermissionsWarning = MutableLiveData(false)

    val screenLockIsSetup = MutableLiveData(false)

    companion object {
        private const val TAG = "SettingsModel"
        
        private const val PREFERENCE_DEVELOPER_MODE_ENABLED = "developer_mode_enabled"
        private const val PREFERENCE_LOGGING_ENABLED = "logging_enabled"
        private const val PREFERENCE_WALLET_SERVER_URL = "wallet_server_url"

        private const val PREFERENCE_FOCUSED_CARD_ID = "focused_card_id"
        private const val PREFERENCE_HIDE_MISSING_PROXIMITY_PERMISSIONS_WARNING =
            "hide_missing_proximity_permissions_warning"

        // Logging
        private const val LOG_FOLDER_NAME = "log"
        private const val LOG_FILE_NAME = "log.txt"

        private const val DEFAULT_WALLET_SERVER_URL = "dev:"
    }

    private val logDir = File(walletApplication.cacheDir, LOG_FOLDER_NAME)
    private val logFile = File(logDir, LOG_FILE_NAME)

    init {
        developerModeEnabled.value =
            sharedPreferences.getBoolean(PREFERENCE_DEVELOPER_MODE_ENABLED, false)
        developerModeEnabled.observeForever { value ->
            sharedPreferences.edit { putBoolean(PREFERENCE_DEVELOPER_MODE_ENABLED, value) }
        }

        focusedCardId.value = sharedPreferences.getString(PREFERENCE_FOCUSED_CARD_ID, "")
        focusedCardId.observeForever { value ->
            sharedPreferences.edit { putString(PREFERENCE_FOCUSED_CARD_ID, value) }
        }

        walletServerUrl.value = sharedPreferences.getString(PREFERENCE_WALLET_SERVER_URL, DEFAULT_WALLET_SERVER_URL)
        walletServerUrl.observeForever { value ->
            sharedPreferences.edit { putString(PREFERENCE_WALLET_SERVER_URL, value) }
        }

        Logger.setLogPrinter(AndroidLogPrinter())
        this.loggingEnabled.value = sharedPreferences.getBoolean(PREFERENCE_LOGGING_ENABLED, false)
        this.loggingEnabled.observeForever { logToFile ->
            sharedPreferences.edit {
                putBoolean(PREFERENCE_LOGGING_ENABLED, logToFile)
            }
            if (logToFile) {
                logDir.mkdirs()
                Logger.startLoggingToFile(logFile)
                Logger.i(TAG, "Started logging to a file")
            } else {
                Logger.i(TAG, "Stopped logging to a file")
                Logger.stopLoggingToFile()
            }
        }

        mrtdSetLogger { level, tag, msg, err ->
            when (level) {
                Log.INFO -> if (err == null) Logger.i(tag, msg) else Logger.i(tag, msg, err)
                Log.DEBUG -> if (err == null) Logger.d(tag, msg) else Logger.d(tag, msg, err)
                Log.WARN -> if (err == null) Logger.w(tag, msg) else Logger.w(tag, msg, err)
                Log.ERROR -> if (err == null) Logger.e(tag, msg) else Logger.e(tag, msg, err)
                else -> throw IllegalArgumentException("Unknown level: $level")
            }
        }

        hideMissingProximityPermissionsWarning.value =
            sharedPreferences.getBoolean(
                PREFERENCE_HIDE_MISSING_PROXIMITY_PERMISSIONS_WARNING,
                false
            )
        hideMissingProximityPermissionsWarning.observeForever {
            sharedPreferences.edit {
                putBoolean(PREFERENCE_HIDE_MISSING_PROXIMITY_PERMISSIONS_WARNING, it)
            }
        }

        updateScreenLockIsSetup()
    }

    // Can be called when entering the application's main activity to update `screenLockIsSetup`.
    // This is for the case where the user deletes the LSKF from the device.
    fun updateScreenLockIsSetup() {
        val keyguardManager = walletApplication.applicationContext
            .getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val value = keyguardManager.isDeviceSecure
        if (value == screenLockIsSetup.value) {
            return
        }
        screenLockIsSetup.value = value
        screenLockIsSetup.postValue(value)
    }

    fun clearLog() {
        if (loggingEnabled.value!!) {
            Logger.stopLoggingToFile()
        }
        logFile.delete()
        if (loggingEnabled.value!!) {
            Logger.startLoggingToFile(logFile)
        }
        Logger.i(TAG, "Log cleared")
    }

    fun createLogSharingIntent(context: Context): Intent {
        // NB: authority must match what given for <provider> in the manifest.
        val authority = "com.android.identity_credential.wallet"
        // NB: must be context for which the <provider> is defined in the manifest.
        val shareUri = FileProvider.getUriForFile(context, authority, logFile);
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.setType("text/plain")
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        sharingIntent.putExtra(Intent.EXTRA_STREAM, shareUri)
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "identity_credential.wallet log")
        return sharingIntent
    }
}