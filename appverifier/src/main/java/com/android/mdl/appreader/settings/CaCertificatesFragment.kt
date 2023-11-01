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
import com.android.mdl.appreader.util.KeysAndCertificates
import com.google.android.material.R
import com.google.android.material.snackbar.Snackbar
import java.security.cert.CertificateException


class CaCertificatesFragment : Fragment() {

    private val viewModel: CaCertificatesViewModel by activityViewModels {
        CaCertificatesViewModel.factory(requireContext())
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
                viewModel.loadCertificates()
                ReaderAppTheme {
                    CaCertificatesScreen(
                        screenState = state,
                        onSelectCertificate = {
                            viewModel.setCurrentCertificateItem(it)
                            openDetails()
                        },
                        onImportCertificate = {
                            fileDialog()
                            viewModel.loadCertificates()
                        },
                        onCopyCertificatesFromResources = { copyCertificatesFromResources() },
                        onDeleteAllCertificates = { deleteAllCertificates() }
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
                VerifierApp.caCertificateStoreInstance.save(inputStream.readBytes())
                // force the trust manager to reload the certificates and vicals
                VerifierApp.trustManagerInstance.reset()
                viewModel.loadCertificates()
            }
        } catch (e: Throwable) {
            showMessage(e.message.toString())
        }
    }

    private fun copyCertificatesFromResources() {
        val certificates = KeysAndCertificates.getTrustedIssuerCertificates(requireContext())
        var imported: Int = 0
        try {
            certificates.forEach {
                if (!VerifierApp.caCertificateStoreInstance.exists(it)) {
                    try {
                        VerifierApp.caCertificateStoreInstance.save(it.encoded)
                        imported++
                    } catch (e: CertificateException) {
                        // ignore validation errors..
                    }
                }
            }
            VerifierApp.trustManagerInstance.reset()
            viewModel.loadCertificates()
            showMessage("$imported certificates were imported")
        } catch (e: Throwable) {
            showMessage(e.message.toString())
        }
    }

    private fun deleteAllCertificates(){
        var deleted = 0
        viewModel.screenState.value.certificates.forEach{
            if (it.certificate != null) {
                VerifierApp.caCertificateStoreInstance.delete(it.certificate)
                deleted++
            }
        }
        VerifierApp.trustManagerInstance.reset()
        viewModel.loadCertificates()
        showMessage("$deleted certificates were deleted")
    }

    private fun showMessage(message: String) {
        val snackbar = Snackbar.make(
            this.requireView(),
            message,
            Snackbar.LENGTH_LONG
        )
        val snackTextView = snackbar.view.findViewById<View>(R.id.snackbar_text) as TextView
        snackTextView.maxLines = 4
        snackbar.show()
    }
}