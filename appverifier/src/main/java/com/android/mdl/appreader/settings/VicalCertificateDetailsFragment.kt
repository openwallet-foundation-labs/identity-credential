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
import com.android.mdl.appreader.theme.ReaderAppTheme

class VicalCertificateDetailsFragment : Fragment() {
    private val viewModel: VicalsViewModel by activityViewModels {
        VicalsViewModel.factory(requireContext())
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
                    CaCertificateDetailsScreen(
                        certificateItem = state,
                        supportsDelete = false
                    )
                }
            }
        }
    }
}