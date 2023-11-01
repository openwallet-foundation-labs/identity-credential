package com.android.mdl.appreader.settings

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
import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.theme.ReaderAppTheme

class CaCertificateDetailsFragment : Fragment() {
    private val viewModel: CaCertificatesViewModel by activityViewModels {
        CaCertificatesViewModel.factory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val state by viewModel.currentCertificateItem.collectAsState()
                ReaderAppTheme {
                    CaCertificateDetailsScreen(certificateItem = state,
                        onDeleteCertificate = { deleteCertificate() })
                }
            }
        }
    }

    private fun deleteCertificate() {
        viewModel.deleteCertificate()
        viewModel.loadCertificates()
        VerifierApp.trustManagerInstance.reset()
        findNavController().popBackStack()
    }
}