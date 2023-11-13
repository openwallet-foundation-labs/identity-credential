package com.android.mdl.appreader.settings

import android.content.ClipboardManager
import android.content.Context
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
        CaCertificatesViewModel.factory()
    }

    private val browseCertificateLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            uris.forEach { uri -> importCertificate(uri) }
            viewModel.loadCertificates()
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
                        onImportCertificate = { fileDialog() },
                        onPasteCertificate = { pasteCertificate() },
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
        browseCertificateLauncher.launch(arrayOf("*/*"))
    }

    private fun importCertificate(uri: Uri) {
        try {
            this.requireContext().contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream != null) {
                    VerifierApp.trustManagerInstance.saveCertificate(inputStream.readBytes())
                }
            }
        } catch (e: Throwable) {
            showException(e)
        }
    }

    private fun pasteCertificate() {
        try {
            val clipboard =
                activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()
                || clipboard.primaryClip?.itemCount == 0
                || clipboard.primaryClip?.getItemAt(0)?.text == null
            ) {
                showMessage("Nothing found to paste")
                return
            }
            val text = clipboard.primaryClip?.getItemAt(0)?.text!!
            VerifierApp.trustManagerInstance.saveCertificate(text.toString().toByteArray())
        } catch (e: Throwable) {
            showException(e)
        } finally {
            viewModel.loadCertificates()
        }
    }

    private fun copyCertificatesFromResources() {
        val certificates = KeysAndCertificates.getTrustedIssuerCertificates(requireContext())
        var imported = 0
        try {
            certificates.forEach {
                if (!VerifierApp.trustManagerInstance.certificateExists(it)) {
                    VerifierApp.trustManagerInstance.saveCertificate(it.encoded)
                    imported++
                }
            }
            showMessage("$imported certificates were imported")
        } catch (e: Throwable) {
            showException(e)
        } finally {
            viewModel.loadCertificates()
        }
    }

    private fun deleteAllCertificates() {
        try {
            var deleted = 0
            viewModel.screenState.value.certificates.forEach {
                if (it.certificate != null) {
                    VerifierApp.trustManagerInstance.deleteCertificate(it.certificate)
                    deleted++
                }
            }
            showMessage("$deleted certificates were deleted")
        } catch (e: Throwable) {
            showException(e)
        } finally {
            viewModel.loadCertificates()
        }
    }

    private fun showException(exception: Throwable) {
        val message = when (exception) {
            is FileAlreadyExistsException -> "The certificate is already in the mDoc Issuer Trust Store"
            is CertificateException -> "The certificate could not be parsed and/or validated correctly"
            else -> exception.message
        }
        showMessage(message.toString())
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