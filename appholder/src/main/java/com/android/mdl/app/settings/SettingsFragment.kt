package com.android.mdl.app.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.android.mdl.app.theme.HolderAppTheme

class SettingsFragment : Fragment() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HolderAppTheme {
                    val state = settingsViewModel.settingsState.collectAsState().value
                    SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        screenState = state,
                        onAutoCloseChanged = settingsViewModel::onConnectionAutoCloseChanged,
                        onUseStaticHandoverChanged = settingsViewModel::onUseStaticHandoverChanged,
                        onUseL2CAPChanged = settingsViewModel::onL2CAPChanged,
                        onBLEDataRetrievalModeChanged = settingsViewModel::onBleDataRetrievalChanged,
                        onBLEServiceCacheChanged = settingsViewModel::onBleServiceCacheChanged,
                        onBLEPeripheralDataRetrievalModeChanged = settingsViewModel::onBlePeripheralModeChanged,
                        onWiFiAwareChanged = settingsViewModel::onWiFiAwareChanged,
                        onNfcChanged = settingsViewModel::onNFCChanged,
                        onDebugChanged = settingsViewModel::onDebugLoggingChanged
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settingsViewModel.loadSettings()
    }
}