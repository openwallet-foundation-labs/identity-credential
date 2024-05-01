package com.android.identity.wallet.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.identity.wallet.theme.HolderAppTheme

class CaCertificateDetailsFragment : Fragment() {
    private val viewModel: CaCertificatesViewModel by activityViewModels {
        CaCertificatesViewModel.factory()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val state by viewModel.currentCertificateItem.collectAsState()
                HolderAppTheme {
                    CaCertificateDetailsScreen(
                        certificateItem = state,
                        onDeleteCertificate = { deleteCertificate() },
                    )
                }
            }
        }
    }

    private fun deleteCertificate() {
        viewModel.deleteCertificate()
        viewModel.loadCertificates()
        findNavController().popBackStack()
    }
}
