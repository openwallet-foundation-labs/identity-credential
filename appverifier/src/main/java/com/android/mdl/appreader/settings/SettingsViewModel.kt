package com.android.mdl.appreader.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val mutableScreenState = MutableStateFlow(SettingsScreenState())
    val screenState: StateFlow<SettingsScreenState> = mutableScreenState

    fun loadSettings() {
        val preferences = readUserPreferences()
        mutableScreenState.value = preferences
    }

    fun onAutoCloseConnectionUpdated(enabled: Boolean) {
        mutableScreenState.update { it.copy(isAutoCloseConnectionEnabled = enabled) }
        userPreferences.setAutoCloseConnectionEnabled(enabled)
    }

    fun onBleL2capUpdated(enabled: Boolean) {
        mutableScreenState.update { it.copy(isL2CAPEnabled = enabled) }
        userPreferences.setBleL2capEnabled(enabled)
    }

    fun onBleClearCacheUpdated(enabled: Boolean) {
        mutableScreenState.update { it.copy(isBleClearCacheEnabled = enabled) }
        userPreferences.setBleClearCacheEnabled(enabled)
    }

    fun onHttpTransferUpdated(enabled: Boolean) {
        if (screenState.value.canToggleHttpTransfer(enabled)) {
            mutableScreenState.update { it.copy(isHttpTransferEnabled = enabled) }
            userPreferences.setHttpTransferEnabled(enabled)
        }
    }

    fun onBleCentralClientModeUpdated(enabled: Boolean) {
        if (screenState.value.canToggleBleCentralClientMode(enabled)) {
            mutableScreenState.update { it.copy(isBleCentralClientModeEnabled = enabled) }
            userPreferences.setBleCentralClientModeEnabled(enabled)
        }
    }

    fun onBlePeripheralClientModeUpdated(enabled: Boolean) {
        if (screenState.value.canToggleBlePeripheralClientMode(enabled)) {
            mutableScreenState.update { it.copy(isBlePeripheralServerMode = enabled) }
            userPreferences.setBlePeripheralClientModeEnabled(enabled)
        }
    }

    fun onWifiAwareUpdated(enabled: Boolean) {
        if (screenState.value.canToggleWifiAware(enabled)) {
            mutableScreenState.update { it.copy(isWifiAwareEnabled = enabled) }
            userPreferences.setWifiAwareEnabled(enabled)
        }
    }

    fun onNfcTransferUpdated(enabled: Boolean) {
        if (screenState.value.canToggleNfcTransfer(enabled)) {
            mutableScreenState.update { it.copy(isNfcTransferEnabled = enabled) }
            userPreferences.setNfcTransferEnabled(enabled)
        }
    }

    fun onDebugLoggingUpdated(enabled: Boolean) {
        mutableScreenState.update { it.copy(isDebugLoggingEnabled = enabled) }
        userPreferences.setDebugLoggingEnabled(enabled)
    }

    fun onReaderAuthenticationUpdated(authentication: Int) {
        mutableScreenState.update { it.copy(readerAuthentication = authentication) }
        userPreferences.setReaderAuthentication(authentication)
    }

    private fun readUserPreferences(): SettingsScreenState {
        val autoClose = userPreferences.isAutoCloseConnectionEnabled()
        val bleL2cap = userPreferences.isBleL2capEnabled()
        val bleClearCache = userPreferences.isBleClearCacheEnabled()
        val httpTransfer = userPreferences.isHttpTransferEnabled()
        val bleCentralMode = userPreferences.isBleCentralClientModeEnabled()
        val blePeripheralMode = userPreferences.isBlePeripheralClientModeEnabled()
        val wifiAware = userPreferences.isWifiAwareEnabled()
        val nfcTransfer = userPreferences.isNfcTransferEnabled()
        val debugLogging = userPreferences.isDebugLoggingEnabled()
        val authentication = userPreferences.getReaderAuthentication()
        return SettingsScreenState(
            isAutoCloseConnectionEnabled = autoClose,
            isL2CAPEnabled = bleL2cap,
            isBleClearCacheEnabled = bleClearCache,
            isHttpTransferEnabled = httpTransfer,
            isBleCentralClientModeEnabled = bleCentralMode,
            isBlePeripheralServerMode = blePeripheralMode,
            isWifiAwareEnabled = wifiAware,
            isNfcTransferEnabled = nfcTransfer,
            isDebugLoggingEnabled = debugLogging,
            readerAuthentication = authentication
        )
    }

    companion object {

        fun factory(userPreferences: UserPreferences): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer { SettingsViewModel(userPreferences) }
            }
        }
    }
}