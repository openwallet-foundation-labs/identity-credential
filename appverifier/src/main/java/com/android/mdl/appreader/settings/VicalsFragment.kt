package com.android.mdl.appreader.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.theme.ReaderAppTheme
import com.google.android.material.R
import com.google.android.material.snackbar.Snackbar

class VicalsFragment : Fragment(){

    private val viewModel: VicalsViewModel by activityViewModels {
        VicalsViewModel.factory(requireContext())
    }

    private val browseCertificateLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            uris.forEach { uri -> importCertificate(uri) }
        }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val state = viewModel.screenState.collectAsState().value
                viewModel.loadVicals()
                ReaderAppTheme {
                    VicalsScreen(
                        screenState = state,
                        onSelectVical = {
                            viewModel.setCurrentVicalItem(it)
                            openDetails()
                        },
                        onImportVical = {
                            fileDialog()
                            viewModel.loadVicals()
                        }
                    )
                }
            }
        }
    }

    private fun openDetails() {
        val destination = CaCertificatesFragmentDirections.toCaCertificateDetails()
        findNavController().navigate(destination)
    }

    private fun fileDialog() {
        browseCertificateLauncher.launch(arrayOf("*/*")) // TODO: maybe more specific...
    }

    private fun importCertificate(uri: Uri) {
        try {
            val inputStream = this.requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                VerifierApp.vicalStoreInstance.save(inputStream.readBytes())
                // force the trust manager to reload the certificates and vicals
                VerifierApp.trustManagerInstance.reset()
                viewModel.loadVicals()
            }
        } catch (e: Throwable) {
            val snackbar = Snackbar.make(
                this.requireView(),
                e.message.toString(),
                Snackbar.LENGTH_LONG
            )
            val snackTextView = snackbar.view.findViewById<View>(R.id.snackbar_text) as TextView
            snackTextView.maxLines = 4
            snackbar.show()
        }
    }
}