package com.android.mdl.appreader.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import org.multipaz.util.Logger

class UserPreferences(
    private val preferences: SharedPreferences
) {

    fun isAutoCloseConnectionEnabled(): Boolean {
        return preferences.getBoolean(AUTO_CLOSE_CONNECTION, false)
    }

    fun setAutoCloseConnectionEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(AUTO_CLOSE_CONNECTION, enabled) }
    }

    fun isBleL2capEnabled(): Boolean {
        return preferences.getBoolean(BLE_DATA_L2CAP, false)
    }

    fun setBleL2capEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(BLE_DATA_L2CAP, enabled) }
    }

    fun isBleClearCacheEnabled(): Boolean {
        return preferences.getBoolean(BLE_CLEAR_CACHE, false)
    }

    fun setBleClearCacheEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(BLE_CLEAR_CACHE, enabled) }
    }

    fun isHttpTransferEnabled(): Boolean {
        return preferences.getBoolean(HTTP_TRANSFER, true)
    }

    fun setHttpTransferEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(HTTP_TRANSFER, enabled) }
    }

    fun isBleCentralClientModeEnabled(): Boolean {
        return preferences.getBoolean(BLE_CENTRAL_CLIENT_MODE, false)
    }

    fun setBleCentralClientModeEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(BLE_CENTRAL_CLIENT_MODE, enabled) }
    }

    fun isBlePeripheralClientModeEnabled(): Boolean {
        return preferences.getBoolean(BLE_PERIPHERAL_CLIENT_MODE, false)
    }

    fun setBlePeripheralClientModeEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(BLE_PERIPHERAL_CLIENT_MODE, enabled) }
    }

    fun isWifiAwareEnabled(): Boolean {
        return preferences.getBoolean(WIFI_AWARE_ENABLED, false)
    }

    fun setWifiAwareEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(WIFI_AWARE_ENABLED, enabled) }
    }

    fun isNfcTransferEnabled(): Boolean {
        return preferences.getBoolean(NFC_TRANSFER_ENABLED, false)
    }

    fun setNfcTransferEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(NFC_TRANSFER_ENABLED, enabled) }
    }

    fun isDebugLoggingEnabled(): Boolean {
        return preferences.getBoolean(LOG_ENABLED, true)
    }

    fun setDebugLoggingEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(LOG_ENABLED, enabled) }
        Logger.isDebugEnabled = enabled
    }

    fun getReaderAuthentication(): Int {
        return preferences.getInt(READER_AUTHENTICATION, 0)
    }

    fun setReaderAuthentication(authentication: Int) {
        preferences.edit { putInt(READER_AUTHENTICATION, authentication) }
    }

    private companion object {
        private const val AUTO_CLOSE_CONNECTION = "auto_close_connection"
        private const val BLE_DATA_L2CAP = "ble_l2cap"
        private const val HTTP_TRANSFER = "http_transfer"
        private const val BLE_CLEAR_CACHE = "ble_clear_cache"
        private const val BLE_CENTRAL_CLIENT_MODE = "ble_central_client_mode"
        private const val BLE_PERIPHERAL_CLIENT_MODE = "ble_peripheral_client_mode"
        private const val WIFI_AWARE_ENABLED = "wifi_aware_enabled"
        private const val NFC_TRANSFER_ENABLED = "nfc_transfer_enabled"
        private const val LOG_ENABLED = "log_enabled"
        private const val READER_AUTHENTICATION = "reader_authentication_mode"
    }
}