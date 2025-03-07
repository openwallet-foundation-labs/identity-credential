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
import org.multipaz.crypto.X509Cert
import org.multipaz.trustmanagement.TrustPoint
import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.theme.ReaderAppTheme
import com.android.mdl.appreader.trustmanagement.getSubjectKeyIdentifier
import com.google.android.material.R
import com.google.android.material.snackbar.Snackbar
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


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
                        onPasteCertificate = { pasteCertificate() }
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
                    val certificate = parseCertificate(inputStream.readBytes())
                    VerifierApp.trustManagerInstance.addTrustPoint(TrustPoint(X509Cert(certificate.encoded)))
                    VerifierApp.certificateStorageEngineInstance.put(
                        certificate.getSubjectKeyIdentifier(),
                        certificate.encoded
                    )
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
            val certificate = parseCertificate(text.toString().toByteArray())
            VerifierApp.trustManagerInstance.addTrustPoint(TrustPoint(X509Cert(certificate.encoded)))
            VerifierApp.certificateStorageEngineInstance.put(
                certificate.getSubjectKeyIdentifier(),
                certificate.encoded
            )
        } catch (e: Throwable) {
            showException(e)
        } finally {
            viewModel.loadCertificates()
        }
    }

    private fun showException(exception: Throwable) {
        val message = when (exception) {
            is FileAlreadyExistsException -> "The certificate is already in the mDoc Issuer Trust Store"
            is CertificateException -> "The certificate could not be parsed correctly"
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

    /**
     * Parse a byte array an X509 certificate
     */
    private fun parseCertificate(certificateBytes: ByteArray): X509Certificate {
        return CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
    }
}