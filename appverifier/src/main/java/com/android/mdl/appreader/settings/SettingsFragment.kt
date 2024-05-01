package com.android.mdl.appreader.settings

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
import androidx.preference.PreferenceManager
import com.android.mdl.appreader.theme.ReaderAppTheme

class SettingsFragment : Fragment() {
    private val userPreferences by lazy {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        UserPreferences(sharedPreferences)
    }
    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(userPreferences)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val state = viewModel.screenState.collectAsState().value
                viewModel.loadSettings()
                ReaderAppTheme {
                    SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        screenState = state,
                        onAutoCloseConnectionChanged = viewModel::onAutoCloseConnectionUpdated,
                        onUseL2CAPChanged = viewModel::onBleL2capUpdated,
                        onBLEServiceCacheChanged = viewModel::onBleClearCacheUpdated,
                        onHttpTransferChanged = viewModel::onHttpTransferUpdated,
                        onBLECentralClientModeChanged = viewModel::onBleCentralClientModeUpdated,
                        onBLEPeripheralServerModeChanged = viewModel::onBlePeripheralClientModeUpdated,
                        onWifiAwareTransferChanged = viewModel::onWifiAwareUpdated,
                        onNfcTransferChanged = viewModel::onNfcTransferUpdated,
                        onDebugLoggingChanged = viewModel::onDebugLoggingUpdated,
                        onChangeReaderAuthentication = viewModel::onReaderAuthenticationUpdated,
                        onOpenCaCertificates = { openCaCertificates() },
                    )
                }
            }
        }
    }

    private fun openCaCertificates() {
        val destination = SettingsFragmentDirections.toCaCertificates()
        findNavController().navigate(destination)
    }
}
