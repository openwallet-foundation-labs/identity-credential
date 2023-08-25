package com.android.mdl.app.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.documentinfo.DocumentInfoScreen
import com.android.mdl.app.documentinfo.DocumentInfoViewModel
import com.android.mdl.app.theme.HolderAppTheme

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