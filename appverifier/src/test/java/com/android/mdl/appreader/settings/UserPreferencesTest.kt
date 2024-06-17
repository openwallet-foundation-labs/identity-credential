package com.android.mdl.appreader.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    private val settingsScreenState = SettingsScreenState(
        isAutoCloseConnectionEnabled = connectionAutoClose,
        isL2CAPEnabled = bleL2CapEnabled,
        isBleClearCacheEnabled = bleClearCacheEnabled,
        isHttpTransferEnabled = httpTransferEnabled,
        isBleCentralClientModeEnabled = bleCentralModeEnabled,
        isBlePeripheralServerMode = blePeripheralModeEnabled,
        isWifiAwareEnabled = wifiAwareEnabled,
        isNfcTransferEnabled = nfcTransferEnabled,
        isDebugLoggingEnabled = debugLogEnabled,
        readerAuthentication = authentication
    )

    private val userPreferences = UserPreferences(InMemorySharedPreferences())

    @Test
    fun defaultSettings() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState())
    }

    @Test
    fun updateAutoCloseConnectionPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onAutoCloseConnectionUpdated(connectionAutoClose)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState(isAutoCloseConnectionEnabled = connectionAutoClose))
    }

    @Test
    fun updateBleL2capPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onBleL2capUpdated(bleL2CapEnabled)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState(isL2CAPEnabled = bleL2CapEnabled))
    }

    @Test
    fun updateBleClearCachePreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onBleClearCacheUpdated(bleClearCacheEnabled)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState(isBleClearCacheEnabled = bleClearCacheEnabled))
    }

    @Test
    fun updateHttpTransferPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences).apply {
            onBleCentralClientModeUpdated(true)
        }

        settingsViewModel.onHttpTransferUpdated(httpTransferEnabled)

        assertEquals(settingsViewModel.screenState.value.isHttpTransferEnabled, httpTransferEnabled)
    }

    @Test
    fun updateBleCentralClientModePreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onBleCentralClientModeUpdated(bleCentralModeEnabled)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState(isBleCentralClientModeEnabled = bleCentralModeEnabled))
    }

    @Test
    fun updateBlePeripheralClientModePreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onBlePeripheralClientModeUpdated(blePeripheralModeEnabled)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState(isBlePeripheralServerMode = blePeripheralModeEnabled))
    }

    @Test
    fun updateWifiAwarePreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onWifiAwareUpdated(wifiAwareEnabled)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState(isWifiAwareEnabled = wifiAwareEnabled))
    }

    @Test
    fun updateNfcTransferPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onNfcTransferUpdated(nfcTransferEnabled)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState(isNfcTransferEnabled = nfcTransferEnabled))
    }

    @Test
    fun updateDebugLoggingPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onDebugLoggingUpdated(debugLogEnabled)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState(isDebugLoggingEnabled = debugLogEnabled))
    }

    @Test
    fun updateReaderAuthenticationPreference() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onReaderAuthenticationUpdated(authentication)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState(readerAuthentication = authentication))
    }

    @Test
    fun preventHttpTransferToggleOffWhenOnlyRetrievalMethod() {
        val settingsViewModel = SettingsViewModel(userPreferences)

        settingsViewModel.onHttpTransferUpdated(false)

        assertEquals(settingsViewModel.screenState.value, SettingsScreenState(isHttpTransferEnabled = true))
    }

    @Test
    fun preventBleCentralClientModeToggleOffWhenOnlyRetrievalMethod() {
        val settingsViewModel = SettingsViewModel(userPreferences).apply {
            onBleCentralClientModeUpdated(true)
            onHttpTransferUpdated(false)
        }

        settingsViewModel.onBleCentralClientModeUpdated(false)

        assertTrue(settingsViewModel.screenState.value.isBleCentralClientModeEnabled)
    }

    @Test
    fun preventBlePeripheralClientModeToggleOffWhenOnlyRetrievalMethod() {
        val settingsViewModel = SettingsViewModel(userPreferences).apply {
            onBlePeripheralClientModeUpdated(true)
            onHttpTransferUpdated(false)
        }

        settingsViewModel.onBlePeripheralClientModeUpdated(false)

        assertTrue(settingsViewModel.screenState.value.isBlePeripheralServerMode)
    }

    @Test
    fun preventWifiAwareToggleOffWhenOnlyRetrievalMethod() {
        val settingsViewModel = SettingsViewModel(userPreferences).apply {
            onWifiAwareUpdated(true)
            onHttpTransferUpdated(false)
        }

        settingsViewModel.onWifiAwareUpdated(false)

        assertTrue(settingsViewModel.screenState.value.isWifiAwareEnabled)
    }

    @Test
    fun preventNfcToggleOffWhenOnlyRetrievalMethod() {
        val settingsViewModel = SettingsViewModel(userPreferences).apply {
            onNfcTransferUpdated(true)
            onHttpTransferUpdated(false)
        }

        settingsViewModel.onNfcTransferUpdated(false)

        assertTrue(settingsViewModel.screenState.value.isNfcTransferEnabled)
    }

    @Test
    fun readPreviouslyStoredPreferences() {
        val settingsViewModel = SettingsViewModel(userPreferences).apply {
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

        assertEquals(settingsViewModel.screenState.value, settingsScreenState)
    }
}