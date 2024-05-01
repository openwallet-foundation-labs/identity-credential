package com.android.mdl.appreader.settings

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class UserPreferencesTest {
    private val connectionAutoClose = true
    private val bleL2CapEnabled = true
    private val bleClearCacheEnabled = true
    private val httpTransferEnabled = false
    private val bleCentralModeEnabled = true
    private val blePeripheralModeEnabled = true
    private val wifiAwareEnabled = true
    private val nfcTransferEnabled = true
    private val debugLogEnabled = false
    private val authentication = 3
    private val settingsScreenState =
        SettingsScreenState(
            isAutoCloseConnectionEnabled = connectionAutoClose,
            isL2CAPEnabled = bleL2CapEnabled,
            isBleClearCacheEnabled = bleClearCacheEnabled,
            isHttpTransferEnabled = httpTransferEnabled,
            isBleCentralClientModeEnabled = bleCentralModeEnabled,
            isBlePeripheralServerMode = blePeripheralModeEnabled,
            isWifiAwareEnabled = wifiAwareEnabled,
            isNfcTransferEnabled = nfcTransferEnabled,
            isDebugLoggingEnabled = debugLogEnabled,
            readerAuthentication = authentication,
        )

    private val userPreferences = UserPreferences(InMemorySharedPreferences())

    @Test
    fun defaultSettings() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState())
    }

    @Test
    fun updateAutoCloseConnectionPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onAutoCloseConnectionUpdated(connectionAutoClose)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState(isAutoCloseConnectionEnabled = connectionAutoClose))
    }

    @Test
    fun updateBleL2capPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onBleL2capUpdated(bleL2CapEnabled)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState(isL2CAPEnabled = bleL2CapEnabled))
    }

    @Test
    fun updateBleClearCachePreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onBleClearCacheUpdated(bleClearCacheEnabled)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState(isBleClearCacheEnabled = bleClearCacheEnabled))
    }

    @Test
    fun updateHttpTransferPreference() {
        val settingsViewModel =
            SettingsViewModel(userPreferences).apply {
                onBleCentralClientModeUpdated(true)
            }

        settingsViewModel.onHttpTransferUpdated(httpTransferEnabled)

        assertThat(settingsViewModel.screenState.value.isHttpTransferEnabled)
            .isEqualTo(httpTransferEnabled)
    }

    @Test
    fun updateBleCentralClientModePreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onBleCentralClientModeUpdated(bleCentralModeEnabled)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState(isBleCentralClientModeEnabled = bleCentralModeEnabled))
    }

    @Test
    fun updateBlePeripheralClientModePreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onBlePeripheralClientModeUpdated(blePeripheralModeEnabled)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState(isBlePeripheralServerMode = blePeripheralModeEnabled))
    }

    @Test
    fun updateWifiAwarePreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onWifiAwareUpdated(wifiAwareEnabled)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState(isWifiAwareEnabled = wifiAwareEnabled))
    }

    @Test
    fun updateNfcTransferPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onNfcTransferUpdated(nfcTransferEnabled)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState(isNfcTransferEnabled = nfcTransferEnabled))
    }

    @Test
    fun updateDebugLoggingPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onDebugLoggingUpdated(debugLogEnabled)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState(isDebugLoggingEnabled = debugLogEnabled))
    }

    @Test
    fun updateReaderAuthenticationPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onReaderAuthenticationUpdated(authentication)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState(readerAuthentication = authentication))
    }

    @Test
    fun preventHttpTransferToggleOffWhenOnlyRetrievalMethod() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onHttpTransferUpdated(false)

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(SettingsScreenState(isHttpTransferEnabled = true))
    }

    @Test
    fun preventBleCentralClientModeToggleOffWhenOnlyRetrievalMethod() {
        val settingsViewModel =
            SettingsViewModel(userPreferences).apply {
                onBleCentralClientModeUpdated(true)
                onHttpTransferUpdated(false)
            }

        settingsViewModel.onBleCentralClientModeUpdated(false)

        assertThat(settingsViewModel.screenState.value.isBleCentralClientModeEnabled).isTrue()
    }

    @Test
    fun preventBlePeripheralClientModeToggleOffWhenOnlyRetrievalMethod() {
        val settingsViewModel =
            SettingsViewModel(userPreferences).apply {
                onBlePeripheralClientModeUpdated(true)
                onHttpTransferUpdated(false)
            }

        settingsViewModel.onBlePeripheralClientModeUpdated(false)

        assertThat(settingsViewModel.screenState.value.isBlePeripheralServerMode).isTrue()
    }

    @Test
    fun preventWifiAwareToggleOffWhenOnlyRetrievalMethod() {
        val settingsViewModel =
            SettingsViewModel(userPreferences).apply {
                onWifiAwareUpdated(true)
                onHttpTransferUpdated(false)
            }

        settingsViewModel.onWifiAwareUpdated(false)

        assertThat(settingsViewModel.screenState.value.isWifiAwareEnabled).isTrue()
    }

    @Test
    fun preventNfcToggleOffWhenOnlyRetrievalMethod() {
        val settingsViewModel =
            SettingsViewModel(userPreferences).apply {
                onNfcTransferUpdated(true)
                onHttpTransferUpdated(false)
            }

        settingsViewModel.onNfcTransferUpdated(false)

        assertThat(settingsViewModel.screenState.value.isNfcTransferEnabled).isTrue()
    }

    @Test
    fun readPreviouslyStoredPreferences() {
        val settingsViewModel =
            SettingsViewModel(userPreferences).apply {
                onAutoCloseConnectionUpdated(connectionAutoClose)
                onBleL2capUpdated(bleL2CapEnabled)
                onBleClearCacheUpdated(bleClearCacheEnabled)
                onBleCentralClientModeUpdated(bleCentralModeEnabled)
                onHttpTransferUpdated(httpTransferEnabled)
                onBlePeripheralClientModeUpdated(blePeripheralModeEnabled)
                onWifiAwareUpdated(wifiAwareEnabled)
                onNfcTransferUpdated(nfcTransferEnabled)
                onDebugLoggingUpdated(debugLogEnabled)
                onReaderAuthenticationUpdated(authentication)
            }

        settingsViewModel.loadSettings()

        assertThat(settingsViewModel.screenState.value)
            .isEqualTo(settingsScreenState)
    }
}
