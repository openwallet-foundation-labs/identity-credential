package com.android.mdl.app.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
@Immutable
data class SettingsScreenState(
    val autoCloseEnabled: Boolean = true,
    val useStaticHandover: Boolean = true,
    val isL2CAPEnabled: Boolean = false,
    val isBleClearCacheEnabled: Boolean = false,
    val isBleDataRetrievalEnabled: Boolean = true,
    val isBlePeripheralModeEnabled: Boolean = false,
    val wifiAwareEnabled: Boolean = false,
    val nfcEnabled: Boolean = false,
    val debugEnabled: Boolean = true
) {

    fun isBleEnabled(): Boolean {
        return isBleDataRetrievalEnabled
                || isBlePeripheralModeEnabled
    }

    fun canToggleBleDataRetrievalMode(newBleCentralMode: Boolean): Boolean {
        val updatedState = copy(isBleDataRetrievalEnabled = newBleCentralMode)
        return updatedState.hasDataRetrieval()
    }

    fun canToggleBlePeripheralMode(newBlePeripheralMode: Boolean): Boolean {
        val updatedState = copy(isBlePeripheralModeEnabled = newBlePeripheralMode)
        return updatedState.hasDataRetrieval()
    }

    fun canToggleWifiAware(newWifiAwareValue: Boolean): Boolean {
        val updatedState = copy(wifiAwareEnabled = newWifiAwareValue)
        return updatedState.hasDataRetrieval()
    }

    fun canToggleNfc(newNfcValue: Boolean): Boolean {
        val updatedState = copy(nfcEnabled = newNfcValue)
        return updatedState.hasDataRetrieval()
    }

    private fun hasDataRetrieval(): Boolean {
        return isBleDataRetrievalEnabled
                || isBlePeripheralModeEnabled
                || wifiAwareEnabled
                || nfcEnabled
    }
}