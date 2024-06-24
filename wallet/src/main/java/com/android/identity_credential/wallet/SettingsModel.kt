package com.android.identity_credential.wallet

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.util.Logging
import com.android.identity.mrtd.mrtdSetLogger
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import com.android.identity_credential.wallet.util.ActivityLogger
import java.io.File
import java.util.EnumMap

data class SettingsModel(
    private val walletApplication: WalletApplication,
    private val sharedPreferences: SharedPreferences
) {
    val developerModeEnabled = MutableLiveData(false)
    val loggingEnabled = MutableLiveData<EnumMap<Logging, Boolean>>(EnumMap(Logging::class.java))
    val walletServerUrl = MutableLiveData<String>(WalletApplicationConfiguration.WALLET_SERVER_DEFAULT_URL)

    val focusedCardId = MutableLiveData("")
    val hideMissingProximityPermissionsWarning = MutableLiveData(false)
    val screenLockIsSetup = MutableLiveData(false)

    companion object {
        private const val TAG = "SettingsModel"
        private const val PREFERENCE_DEVELOPER_MODE_ENABLED = "developer_mode_enabled"
        private const val PREFERENCE_DIAGNOSTIC_LOGGING_ENABLED = "diagnostic_logging_enabled"
        private const val PREFERENCE_WALLET_SERVER_URL = "wallet_server_url"

        private const val PREFERENCE_FOCUSED_CARD_ID = "focused_card_id"
        private const val PREFERENCE_HIDE_MISSING_PROXIMITY_PERMISSIONS_WARNING = "hide_missing_proximity_permissions_warning"

        // Logging
        private const val LOG_FOLDER_NAME = "log"
        private const val DIAGNOSTIC_LOG_FILE_NAME = "diagnostic_log.txt"
        private const val ACTIVITY_LOG_FILE_NAME = "activity_log.txt"

        private const val DEFAULT_WALLET_SERVER_URL = "dev:"
    }

    private val logDir = Path(walletApplication.cacheDir.path, LOG_FOLDER_NAME)
    private val diagnosticLogFile = Path(logDir, DIAGNOSTIC_LOG_FILE_NAME)
    private val activityLogDir = Path(walletApplication.filesDir.path)
    private val activityLogFile = Path(activityLogDir, ACTIVITY_LOG_FILE_NAME)

    init {
        // Initialize settings
        developerModeEnabled.value = sharedPreferences.getBoolean(PREFERENCE_DEVELOPER_MODE_ENABLED, false)
        focusedCardId.value = sharedPreferences.getString(PREFERENCE_FOCUSED_CARD_ID, "")
        walletServerUrl.value = sharedPreferences.getString(PREFERENCE_WALLET_SERVER_URL, DEFAULT_WALLET_SERVER_URL)
        hideMissingProximityPermissionsWarning.value = sharedPreferences.getBoolean(PREFERENCE_HIDE_MISSING_PROXIMITY_PERMISSIONS_WARNING, false)

        // Observe changes and save to SharedPreferences
        developerModeEnabled.observeForever { value ->
            sharedPreferences.edit { putBoolean(PREFERENCE_DEVELOPER_MODE_ENABLED, value) }
        }
        focusedCardId.observeForever { value ->
            sharedPreferences.edit { putString(PREFERENCE_FOCUSED_CARD_ID, value) }
        }

        walletServerUrl.value = sharedPreferences.getString(
            PREFERENCE_WALLET_SERVER_URL,
            WalletApplicationConfiguration.WALLET_SERVER_DEFAULT_URL)
        walletServerUrl.observeForever { value ->
            sharedPreferences.edit { putString(PREFERENCE_WALLET_SERVER_URL, value) }
        }
        hideMissingProximityPermissionsWarning.observeForever {
            sharedPreferences.edit {
                putBoolean(PREFERENCE_HIDE_MISSING_PROXIMITY_PERMISSIONS_WARNING, it)
            }
        }

        // Load and update from SharedPreferences (overrides defaults if found)
        for (loggingType in Logging.values()) {
            val enabled = sharedPreferences.getBoolean(
                "${PREFERENCE_DIAGNOSTIC_LOGGING_ENABLED}_${loggingType.name}",
                false // Default to false if not found
            )
            loggingEnabled.value?.set(loggingType, enabled)
        }
        // Observe changes to loggingEnabled and save to SharedPreferences
        loggingEnabled.observeForever { loggingMap ->
            for ((loggingType, enabled) in loggingMap) {  // Iterate through the map
                val prefKey = "${PREFERENCE_DIAGNOSTIC_LOGGING_ENABLED}_${loggingType.name}"
                val currentStoredValue = sharedPreferences.getBoolean(prefKey, false)

                if (currentStoredValue == enabled) {
                    continue
                }
                sharedPreferences.edit {
                    putBoolean(prefKey, enabled)
                }
                // Update logging based on the logging type
                if (enabled) {
                    // Handle logging start for specific type if needed
                    when (loggingType) {
                        Logging.VERIFY -> {
                            ActivityLogger.startLoggingActivityToFile(activityLogFile, Logging.VERIFY)
                        }
                        Logging.UPDATE -> {
                            ActivityLogger.startLoggingActivityToFile(activityLogFile, Logging.UPDATE)
                        }
                        Logging.PRESENTATION -> {
                            ActivityLogger.startLoggingActivityToFile(activityLogFile, Logging.PRESENTATION)
                        }
                        Logging.COMMUNICATION -> {
                            ActivityLogger.startLoggingActivityToFile(activityLogFile, Logging.COMMUNICATION)
                        }
                        else -> {}
                    }
                } else {
                    // Handle logging stop for specific type if needed
                    when (loggingType) {
                        Logging.VERIFY -> {
                            ActivityLogger.stopLoggingActivityToFile(activityLogFile, Logging.VERIFY)
                        }
                        Logging.UPDATE -> {
                            ActivityLogger.stopLoggingActivityToFile(activityLogFile, Logging.UPDATE)
                        }
                        Logging.PRESENTATION -> {
                            ActivityLogger.stopLoggingActivityToFile(activityLogFile, Logging.PRESENTATION)
                        }
                        Logging.COMMUNICATION -> {
                            ActivityLogger.stopLoggingActivityToFile(activityLogFile, Logging.COMMUNICATION)
                        }
                        else -> {}
                    }
                }
            }
        }

        Logger.setLogPrinter(AndroidLogPrinter())

        mrtdSetLogger { level, tag, msg, err ->
            when (level) {
                Log.INFO -> if (err == null) Logger.i(tag, msg) else Logger.i(tag, msg, err)
                Log.DEBUG -> if (err == null) Logger.d(tag, msg) else Logger.d(tag, msg, err)
                Log.WARN -> if (err == null) Logger.w(tag, msg) else Logger.w(tag, msg, err)
                Log.ERROR -> if (err == null) Logger.e(tag, msg) else Logger.e(tag, msg, err)
                else -> throw IllegalArgumentException("Unknown level: $level")
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
        if (loggingEnabled.value?.get(Logging.DIAGNOSTIC) == true) {  // Specific check for DIAGNOSTIC
            Logger.stopLoggingToFile()
        }

        // Assuming you only want to delete the diagnostic log file
        SystemFileSystem.delete(diagnosticLogFile)

        if (loggingEnabled.value?.get(Logging.DIAGNOSTIC) == true) {
            Logger.startLoggingToFile(diagnosticLogFile)
        }
    }

    fun createLogSharingIntent(context: Context): Intent {
        val authority = "com.android.identity_credential.wallet"
        // NB: must be context for which the <provider> is defined in the manifest.
        val shareUri = FileProvider.getUriForFile(context, authority, File(diagnosticLogFile.name))
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.setType("text/plain")
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        sharingIntent.putExtra(Intent.EXTRA_STREAM, shareUri)
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "identity_credential.wallet log")
        return sharingIntent
    }

    fun setLoggingEnabled(loggingType: Logging, enabled: Boolean) {
        val loggingMap = loggingEnabled.value ?: EnumMap(Logging::class.java)
        loggingMap[loggingType] = enabled
        loggingEnabled.value = loggingMap

        // Save to SharedPreferences
        sharedPreferences.edit {
            putBoolean("${PREFERENCE_DIAGNOSTIC_LOGGING_ENABLED}_${loggingType.name}", enabled)
        }

        // Handle logging start/stop
        if (enabled) {
            if (loggingType == Logging.DIAGNOSTIC) {
                SystemFileSystem.createDirectories(logDir)
                Logger.startLoggingToFile(diagnosticLogFile)
            }
        } else {
            if (loggingType == Logging.DIAGNOSTIC) {
                Logger.stopLoggingToFile()
            }
        }
    }
}
