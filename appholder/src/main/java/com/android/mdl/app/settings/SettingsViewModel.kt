package com.android.mdl.app.settings

import androidx.lifecycle.ViewModel
import com.android.mdl.app.util.PreferencesHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel : ViewModel() {

    private val mutableSettingsState = MutableStateFlow(SettingsScreenState())
    val settingsState: StateFlow<SettingsScreenState> = mutableSettingsState

    fun loadSettings() {
        val settingsState = SettingsScreenState(
            autoCloseEnabled = PreferencesHelper.isConnectionAutoCloseEnabled(),
            useStaticHandover = PreferencesHelper.shouldUseStaticHandover(),
            isL2CAPEnabled = PreferencesHelper.isBleL2capEnabled(),
            isBleClearCacheEnabled = PreferencesHelper.isBleClearCacheEnabled(),
            isBleDataRetrievalEnabled = PreferencesHelper.isBleDataRetrievalEnabled(),
            isBlePeripheralModeEnabled = PreferencesHelper.isBleDataRetrievalPeripheralModeEnabled(),
            wifiAwareEnabled = PreferencesHelper.isWifiDataRetrievalEnabled(),
            nfcEnabled = PreferencesHelper.isNfcDataRetrievalEnabled(),
            debugEnabled = PreferencesHelper.isDebugLoggingEnabled()
        )
        mutableSettingsState.value = settingsState
    }

    fun onConnectionAutoCloseChanged(newValue: Boolean) {
        PreferencesHelper.setConnectionAutoCloseEnabled(newValue)
        mutableSettingsState.update { it.copy(autoCloseEnabled = newValue) }
    }

    fun onUseStaticHandoverChanged(newValue: Boolean) {
        PreferencesHelper.setUseStaticHandover(newValue)
        mutableSettingsState.update { it.copy(useStaticHandover = newValue) }
    }

    fun onL2CAPChanged(newValue: Boolean) {
        PreferencesHelper.setBleL2CAPEnabled(newValue)
        mutableSettingsState.update { it.copy(isL2CAPEnabled = newValue) }
    }

    fun onBleServiceCacheChanged(newValue: Boolean) {
        PreferencesHelper.setBleClearCacheEnabled(newValue)
        mutableSettingsState.update { it.copy(isBleClearCacheEnabled = newValue) }
    }

    fun onBleDataRetrievalChanged(newValue: Boolean) {
        val state = mutableSettingsState.value
        if (state.canToggleBleDataRetrievalMode(newValue)) {
            PreferencesHelper.setBleDataRetrievalEnabled(newValue)
            mutableSettingsState.update { it.copy(isBleDataRetrievalEnabled = newValue) }
        }
    }

    fun onBlePeripheralModeChanged(newValue: Boolean) {
        val state = mutableSettingsState.value
        if (state.canToggleBlePeripheralMode(newValue)) {
            PreferencesHelper.setBlePeripheralDataRetrievalMode(newValue)
            mutableSettingsState.update { it.copy(isBlePeripheralModeEnabled = newValue) }
        }
    }

    fun onWiFiAwareChanged(newValue: Boolean) {
        val state = mutableSettingsState.value
        if (state.canToggleWifiAware(newValue)) {
            PreferencesHelper.setWifiDataRetrievalEnabled(newValue)
            mutableSettingsState.update { it.copy(wifiAwareEnabled = newValue) }
        }
    }

    fun onNFCChanged(newValue: Boolean) {
        val state = mutableSettingsState.value
        if (state.canToggleNfc(newValue)) {
            PreferencesHelper.setNfcDataRetrievalEnabled(newValue)
            mutableSettingsState.update { it.copy(nfcEnabled = newValue) }
        }
    }

    fun onDebugLoggingChanged(newValue: Boolean) {
        PreferencesHelper.setDebugLoggingEnabled(newValue)
        mutableSettingsState.update { it.copy(debugEnabled = newValue) }
    }
}

