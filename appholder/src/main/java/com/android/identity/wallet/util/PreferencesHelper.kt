package com.android.identity.wallet.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.android.identity.securearea.EcCurve
import com.android.identity.util.Logger
import java.io.File

object PreferencesHelper {
    private const val BLE_DATA_RETRIEVAL = "ble_transport"
    private const val BLE_DATA_RETRIEVAL_PERIPHERAL_MODE = "ble_transport_peripheral_mode"
    private const val BLE_DATA_L2CAP = "ble_l2cap"
    private const val BLE_CLEAR_CACHE = "ble_clear_cache"
    private const val WIFI_DATA_RETRIEVAL = "wifi_transport"
    private const val NFC_DATA_RETRIEVAL = "nfc_transport"
    private const val DEBUG_LOG = "debug_log"
    private const val CONNECTION_AUTO_CLOSE = "connection_auto_close"
    private const val STATIC_HANDOVER = "static_handover"
    private const val EPHEMERAL_KEY_CURVE_OPTION = "ephemeral_key_curve"

    private lateinit var sharedPreferences: SharedPreferences

    fun initialize(context: Context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    fun getKeystoreBackedStorageLocation(context: Context): File {
        // As per the docs, the credential data contains reference to Keystore aliases so ensure
        // this is stored in a location where it's not automatically backed up and restored by
        // Android Backup as per https://developer.android.com/guide/topics/data/autobackup
        val storageDir = File(context.noBackupFilesDir, "identity")
        if (!storageDir.exists()) {
            storageDir.mkdir()
        }
        return storageDir;
    }

    fun isBleDataRetrievalEnabled(): Boolean =
        sharedPreferences.getBoolean(BLE_DATA_RETRIEVAL, true)

    fun setBleDataRetrievalEnabled(enabled: Boolean) =
        sharedPreferences.edit { putBoolean(BLE_DATA_RETRIEVAL, enabled) }

    fun isBleDataRetrievalPeripheralModeEnabled(): Boolean =
        sharedPreferences.getBoolean(BLE_DATA_RETRIEVAL_PERIPHERAL_MODE, false)

    fun setBlePeripheralDataRetrievalMode(enabled: Boolean) =
        sharedPreferences.edit { putBoolean(BLE_DATA_RETRIEVAL_PERIPHERAL_MODE, enabled) }

    fun isBleL2capEnabled(): Boolean =
        sharedPreferences.getBoolean(BLE_DATA_L2CAP, false)

    fun setBleL2CAPEnabled(enabled: Boolean) =
        sharedPreferences.edit { putBoolean(BLE_DATA_L2CAP, enabled) }

    fun isBleClearCacheEnabled(): Boolean =
        sharedPreferences.getBoolean(BLE_CLEAR_CACHE, false)

    fun setBleClearCacheEnabled(enabled: Boolean) =
        sharedPreferences.edit { putBoolean(BLE_CLEAR_CACHE, enabled) }

    fun isWifiDataRetrievalEnabled(): Boolean =
        sharedPreferences.getBoolean(WIFI_DATA_RETRIEVAL, false)

    fun setWifiDataRetrievalEnabled(enabled: Boolean) =
        sharedPreferences.edit { putBoolean(WIFI_DATA_RETRIEVAL, enabled) }

    fun isNfcDataRetrievalEnabled(): Boolean =
        sharedPreferences.getBoolean(NFC_DATA_RETRIEVAL, false)

    fun setNfcDataRetrievalEnabled(enabled: Boolean) =
        sharedPreferences.edit { putBoolean(NFC_DATA_RETRIEVAL, enabled) }

    fun isConnectionAutoCloseEnabled(): Boolean =
        sharedPreferences.getBoolean(CONNECTION_AUTO_CLOSE, true)

    fun setConnectionAutoCloseEnabled(enabled: Boolean) =
        sharedPreferences.edit { putBoolean(CONNECTION_AUTO_CLOSE, enabled) }

    fun shouldUseStaticHandover(): Boolean =
        sharedPreferences.getBoolean(STATIC_HANDOVER, false)

    fun setUseStaticHandover(enabled: Boolean) =
        sharedPreferences.edit { putBoolean(STATIC_HANDOVER, enabled) }

    fun isDebugLoggingEnabled(): Boolean =
        sharedPreferences.getBoolean(DEBUG_LOG, true)

    fun setDebugLoggingEnabled(enabled: Boolean) =
        sharedPreferences
            .edit { putBoolean(DEBUG_LOG, enabled) }
            .also { Logger.isDebugEnabled = enabled }

    fun getEphemeralKeyCurveOption(): EcCurve =
        EcCurve.fromInt(
            sharedPreferences.getInt(
                EPHEMERAL_KEY_CURVE_OPTION,
                EcCurve.P256.coseCurveIdentifier
            )
        )

    fun setEphemeralKeyCurveOption(newValue: EcCurve) =
        sharedPreferences.edit { putInt(EPHEMERAL_KEY_CURVE_OPTION, newValue.coseCurveIdentifier) }
}