package org.multipaz_credential.wallet

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.android.util.AndroidLogPrinter
import org.multipaz.mdoc.connectionmethod.ConnectionMethod
import org.multipaz.mdoc.connectionmethod.ConnectionMethodBle
import org.multipaz.util.Logger
import org.multipaz.mrtd.mrtdSetLogger
import org.multipaz.util.UUID
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.io.File

class SettingsModel(
    private val walletApplication: WalletApplication,
    private val sharedPreferences: SharedPreferences
) {
    // Settings that are visible in the Settings screen
    val developerModeEnabled = MutableLiveData(false)
    val loggingEnabled = MutableLiveData(false)
    val activityLoggingEnabled = MutableLiveData(false)
    val walletServerUrl = MutableLiveData(WalletApplicationConfiguration.WALLET_SERVER_DEFAULT_URL)
    val minServerUrl = MutableLiveData(WalletApplicationConfiguration.MIN_SERVER_DEFAULT_URL)
    val cloudSecureAreaUrl = MutableLiveData(WalletApplicationConfiguration.CLOUD_SECURE_AREA_DEFAULT_URL)

    // Settings that are visible in the Proximity Sharing Settings screen
    val nfcStaticHandoverEnabled = MutableLiveData(false)
    val bleCentralClientMode = MutableLiveData(false)
    val blePeripheralServerMode = MutableLiveData(false)
    val bleL2CAP = MutableLiveData(false)

    // Non visible in the Settings screen
    val focusedCardId = MutableLiveData("")
    val hideMissingProximityPermissionsWarning = MutableLiveData(false)
    val hideMissingBluetoothPermissionsWarning = MutableLiveData(false)

    val screenLockIsSetup = MutableLiveData(false)

    companion object {
        private const val TAG = "SettingsModel"
        private const val PREFERENCE_DEVELOPER_MODE_ENABLED = "developer_mode_enabled"
        private const val PREFERENCE_LOGGING_ENABLED = "logging_enabled"
        private const val PREFERENCE_ACTIVITY_LOGGING_ENABLED = "activity_logging_enabled"
        private const val PREFERENCE_WALLET_SERVER_URL = "wallet_server_url"
        private const val PREFERENCE_MIN_SERVER_URL = "min_server_url"
        private const val PREFERENCE_CLOUD_SECURE_AREA_URL = "cloud_secure_area_url"

        private const val PREFERENCE_NFC_STATIC_HANDOVER_ENABLED = "nfc_static_handover_enabled"
        private const val PREFERENCE_BLE_CENTRAL_CLIENT_MODE_ENABLED = "ble_central_client_mode_enabled"
        private const val PREFERENCE_BLE_PERIPHERAL_SERVER_MODE_ENABLED = "ble_peripheral_server_mode_enabled"
        private const val PREFERENCE_BLE_L2CAP_ENABLED = "ble_l2cap_enabled"

        private const val PREFERENCE_FOCUSED_CARD_ID = "focused_card_id"
        private const val PREFERENCE_HIDE_MISSING_PROXIMITY_PERMISSIONS_WARNING =
            "hide_missing_proximity_permissions_warning"

        // Logging
        private const val LOG_FOLDER_NAME = "log"
        private const val LOG_FILE_NAME = "log.txt"
    }

    private val logDir = Path(walletApplication.cacheDir.path, LOG_FOLDER_NAME)
    private val logFile = Path(logDir, LOG_FILE_NAME)

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

        walletServerUrl.value = sharedPreferences.getString(
            PREFERENCE_WALLET_SERVER_URL,
            WalletApplicationConfiguration.WALLET_SERVER_DEFAULT_URL)
        walletServerUrl.observeForever { value ->
            sharedPreferences.edit { putString(PREFERENCE_WALLET_SERVER_URL, value) }
        }

        cloudSecureAreaUrl.value = sharedPreferences.getString(
            PREFERENCE_CLOUD_SECURE_AREA_URL,
            WalletApplicationConfiguration.CLOUD_SECURE_AREA_DEFAULT_URL)
        cloudSecureAreaUrl.observeForever { value ->
            sharedPreferences.edit { putString(PREFERENCE_CLOUD_SECURE_AREA_URL, value) }
        }

        minServerUrl.value = sharedPreferences.getString(
            PREFERENCE_MIN_SERVER_URL,
            WalletApplicationConfiguration.MIN_SERVER_DEFAULT_URL)
        minServerUrl.observeForever { value ->
            sharedPreferences.edit { putString(PREFERENCE_MIN_SERVER_URL, value) }
        }

        Logger.setLogPrinter(AndroidLogPrinter())
        this.loggingEnabled.value = sharedPreferences.getBoolean(PREFERENCE_LOGGING_ENABLED, false)
        this.loggingEnabled.observeForever { logToFile ->
            sharedPreferences.edit {
                putBoolean(PREFERENCE_LOGGING_ENABLED, logToFile)
            }
            if (logToFile) {
                SystemFileSystem.createDirectories(logDir)
                Logger.startLoggingToFile(logFile)
                Logger.i(TAG, "Started logging to a file $logFile")
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

        // Observe changes to Activity Logging and save to SharedPreferences
        this.activityLoggingEnabled.value = sharedPreferences.getBoolean(PREFERENCE_ACTIVITY_LOGGING_ENABLED, true)
        this.activityLoggingEnabled.observeForever { activityLogging ->
            sharedPreferences.edit {
                putBoolean(PREFERENCE_ACTIVITY_LOGGING_ENABLED, activityLogging)
            }
            if (activityLogging) {
                walletApplication.eventLogger.startLoggingEvents()
            } else {
                walletApplication.eventLogger.stopLoggingEvents()
            }
        }
        updateScreenLockIsSetup()

        bindBoolean(nfcStaticHandoverEnabled, PREFERENCE_NFC_STATIC_HANDOVER_ENABLED, false)
        bindBoolean(bleCentralClientMode, PREFERENCE_BLE_CENTRAL_CLIENT_MODE_ENABLED, true)
        bindBoolean(blePeripheralServerMode, PREFERENCE_BLE_PERIPHERAL_SERVER_MODE_ENABLED, false)
        bindBoolean(bleL2CAP, PREFERENCE_BLE_L2CAP_ENABLED, false)
    }

    private fun bindBoolean(
        liveData: MutableLiveData<Boolean>,
        setting: String,
        defaultValue: Boolean
    ) {
        liveData.value = sharedPreferences.getBoolean(setting, defaultValue)
        liveData.observeForever { enabled ->
            sharedPreferences.edit {
                putBoolean(setting, enabled)
            }
        }
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
        SystemFileSystem.delete(logFile)
        if (loggingEnabled.value!!) {
            Logger.startLoggingToFile(logFile)
        }
        Logger.i(TAG, "Log cleared")
    }

    fun createLogSharingIntent(context: Context): Intent {
        // NB: authority must match what given for <provider> in the manifest.
        val authority = BuildConfig.javaClass.`package`!!.name
        // NB: must be context for which the <provider> is defined in the manifest.
        val shareUri = FileProvider.getUriForFile(context, authority, File(logFile.toString()))
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.setType("text/plain")
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        sharingIntent.putExtra(Intent.EXTRA_STREAM, shareUri)
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "identity_credential.wallet log")
        return sharingIntent
    }

    fun createConnectionMethodsAndOptions(): Pair<List<ConnectionMethod>, DataTransportOptions> {
        val connectionMethods = mutableListOf<ConnectionMethod>()
        if (bleCentralClientMode.value == true && blePeripheralServerMode.value == true) {
            val bleUuid = UUID.randomUUID()
            connectionMethods.add(ConnectionMethodBle(true, true, bleUuid, bleUuid))
        } else if (bleCentralClientMode.value == true) {
            val bleUuid = UUID.randomUUID()
            connectionMethods.add(ConnectionMethodBle(false, true, null, bleUuid))
        } else if (blePeripheralServerMode.value == true) {
            val bleUuid = UUID.randomUUID()
            connectionMethods.add(ConnectionMethodBle(true, false, bleUuid, null))
        }

        val optionsBuilder = DataTransportOptions.Builder()
        if (bleL2CAP.value == true) {
            optionsBuilder.setBleUseL2CAP(true)
            optionsBuilder.setExperimentalBleL2CAPPsmInEngagement(true)
        }
        return Pair(connectionMethods, optionsBuilder.build())
    }
}
