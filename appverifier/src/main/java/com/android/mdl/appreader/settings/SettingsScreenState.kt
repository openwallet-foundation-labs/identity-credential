package com.android.mdl.appreader.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
@Immutable
data class SettingsScreenState(
    val isAutoCloseConnectionEnabled: Boolean = false,
    val isL2CAPEnabled: Boolean = false,
    val isBleClearCacheEnabled: Boolean = false,
    val isHttpTransferEnabled: Boolean = true,
    val isBleCentralClientModeEnabled: Boolean = false,
    val isBlePeripheralServerMode: Boolean = false,
    val isWifiAwareEnabled: Boolean = false,
    val isNfcTransferEnabled: Boolean = false,
    val isDebugLoggingEnabled: Boolean = true,
    val readerAuthentication: Int = 0
) {

    fun canToggleHttpTransfer(newValue: Boolean): Boolean {
        val updatedState = copy(isHttpTransferEnabled = newValue)
        return updatedState.hasDataRetrievalOn()
    }

    fun canToggleBleCentralClientMode(newValue: Boolean): Boolean {
        val updatedState = copy(isBleCentralClientModeEnabled = newValue)
        return updatedState.hasDataRetrievalOn()
    }

    fun canToggleBlePeripheralClientMode(newValue: Boolean): Boolean {
        val updatedState = copy(isBlePeripheralServerMode = newValue)
        return updatedState.hasDataRetrievalOn()
    }

    fun canToggleWifiAware(newValue: Boolean): Boolean {
        val updatedState = copy(isWifiAwareEnabled = newValue)
        return updatedState.hasDataRetrievalOn()
    }

    fun canToggleNfcTransfer(newValue: Boolean): Boolean {
        val updatedState = copy(isNfcTransferEnabled = newValue)
        return updatedState.hasDataRetrievalOn()
    }

    private fun hasDataRetrievalOn(): Boolean {
        return isHttpTransferEnabled
                || isBleCentralClientModeEnabled
                || isBlePeripheralServerMode
                || isWifiAwareEnabled
                || isNfcTransferEnabled
    }
}