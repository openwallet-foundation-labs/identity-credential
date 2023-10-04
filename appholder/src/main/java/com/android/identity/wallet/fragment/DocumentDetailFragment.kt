package com.android.identity.wallet.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.identity.wallet.document.DocumentManager
import com.android.identity.wallet.documentinfo.DocumentInfoScreen
import com.android.identity.wallet.documentinfo.DocumentInfoViewModel
import com.android.identity.wallet.theme.HolderAppTheme

class DocumentDetailFragment : Fragment() {

    private val args: DocumentDetailFragmentArgs by navArgs()
    private val viewModel by viewModels<DocumentInfoViewModel> {
        val documentManager = DocumentManager.getInstance(requireContext())
        DocumentInfoViewModel.Factory(documentManager)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HolderAppTheme {
                    DocumentInfoScreen(
                        viewModel = viewModel,
                        onNavigateUp = { findNavController().navigateUp() },
                        onNavigateToDocumentDetails = { onShowData(args.documentName) }
                    )
                }
            }
            viewModel.loadDocument(args.documentName)
        }
    }

    private fun onShowData(documentName: String) {
        val direction = DocumentDetailFragmentDirections
            .navigateToDocumentData(documentName)
        findNavController().navigate(direction)
    }
}