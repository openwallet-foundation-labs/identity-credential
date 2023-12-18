package com.android.identity.wallet.settings

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
import androidx.navigation.fragment.findNavController
import com.android.identity.wallet.theme.HolderAppTheme

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
                        onSessionEncryptionCurveChanged = settingsViewModel::onEphemeralKeyCurveChanged,
                        onUseStaticHandoverChanged = settingsViewModel::onUseStaticHandoverChanged,
                        onUseL2CAPChanged = settingsViewModel::onL2CAPChanged,
                        onBLEDataRetrievalModeChanged = settingsViewModel::onBleDataRetrievalChanged,
                        onBLEServiceCacheChanged = settingsViewModel::onBleServiceCacheChanged,
                        onBLEPeripheralDataRetrievalModeChanged = settingsViewModel::onBlePeripheralModeChanged,
                        onWiFiAwareChanged = settingsViewModel::onWiFiAwareChanged,
                        onNfcChanged = settingsViewModel::onNFCChanged,
                        onDebugChanged = settingsViewModel::onDebugLoggingChanged,
                        onOpenCaCertificates = {openCaCertificates()},
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settingsViewModel.loadSettings()
    }

    private fun openCaCertificates(){
        val destination = SettingsFragmentDirections.toCaCertificates()
        findNavController().navigate(destination)
    }
}