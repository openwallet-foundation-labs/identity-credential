package com.android.mdl.app.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.authprompt.UserAuthPromptBuilder
import com.android.mdl.app.databinding.FragmentTransferDocumentBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.KeysAndCertificates
import com.android.mdl.app.readerauth.SimpleReaderTrustStore
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus
import com.android.mdl.app.viewmodel.TransferDocumentViewModel
import org.jetbrains.anko.support.v4.runOnUiThread

class TransferDocumentFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "TransferDocumentFragment"
    }

    private var _binding: FragmentTransferDocumentBinding? = null
    private var readerCommonName = ""
    private var readerIsTrusted: Boolean = false
    private val viewModel: TransferDocumentViewModel by viewModels()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferDocumentBinding.inflate(inflater)
        binding.fragment = this
        binding.vm = viewModel
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getTransferStatus().observe(viewLifecycleOwner) { transferStatus ->
            when (transferStatus) {
                TransferStatus.QR_ENGAGEMENT_READY -> Log.d(LOG_TAG, "Engagement Ready")
                TransferStatus.CONNECTED -> Log.d(LOG_TAG, "Connected")
                TransferStatus.REQUEST -> onTransferRequested()
                TransferStatus.DISCONNECTED -> onTransferDisconnected()
                TransferStatus.ERROR -> onTransferError()
            }
        }
    }

    private fun onTransferRequested() {
        Log.d(LOG_TAG, "Request")
        // TODO: Add option to the user select the which document share when there
        //  are more than one for now we are just returning the first document we found
        try {
            val trustStore = SimpleReaderTrustStore(
                KeysAndCertificates.getTrustedReaderCertificates(requireContext())
            )
            val requestedDocuments = viewModel.getRequestedDocuments()
            requestedDocuments.forEach { reqDoc ->
                val doc = viewModel.getDocuments().find { reqDoc.docType == it.docType }
                if (reqDoc.readerAuth != null && reqDoc.readerAuthenticated) {
                    val readerChain = reqDoc.readerCertificateChain
                    if (readerChain != null) {
                        val trustPath = trustStore.createCertificationTrustPath(readerChain.toList())
                        // Look for the common name in the root certificate if it is trusted or not
                        val certChain = if (trustPath?.isNotEmpty() == true) {
                            trustPath
                        } else {
                            readerChain
                        }

                        certChain.first().subjectX500Principal.name.split(",")
                            .forEach { line ->
                                val (key, value) = line.split("=", limit = 2)
                                if (key == "CN") {
                                    readerCommonName = value
                                }
                            }

                        // Add some information about the reader certificate used
                        if (trustStore.validateCertificationTrustPath(trustPath)) {
                            readerIsTrusted = true
                            binding.txtDocuments.append("- Trusted reader auth used: ($readerCommonName)\n")
                        } else {
                            readerIsTrusted = false
                            binding.txtDocuments.append("- Not trusted reader auth used: ($readerCommonName)\n")
                        }
                    }
                }
                if (doc != null) {
                    binding.txtDocuments.append("- ${doc.userVisibleName} (${doc.docType})\n")
                } else {
                    binding.txtDocuments.append("- No document found for ${reqDoc.docType}\n")
                }
            }

            // TODO Request the data, iterate over it and see which properties need signature
            // Then ask for user consent and send response

            if (viewModel.sendResponse()) {
                requestUserAuth(false)
            }
        } catch (e: Exception) {
            val message = "On request received error: ${e.message}"
            Log.e(LOG_TAG, message, e)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            binding.txtConnectionStatus.append("\n$message")
        }
    }

    private fun onTransferDisconnected() {
        Log.d(LOG_TAG, "Disconnected")
        hideButtons()
        TransferManager.getInstance(requireContext()).disconnect()
    }

    private fun onTransferError() {
        Toast.makeText(requireContext(), "An error occurred.", Toast.LENGTH_SHORT).show()
        hideButtons()
        TransferManager.getInstance(requireContext()).disconnect()
    }

    private fun hideButtons() {
        binding.txtConnectionStatus.text = getString(R.string.connection_mdoc_closed)
        binding.btCloseConnection.visibility = View.GONE
        binding.btCloseTerminationMessage.visibility = View.GONE
        binding.btCloseTransportSpecific.visibility = View.GONE
        binding.btOk.visibility = View.VISIBLE
    }

    private fun requestUserAuth(forceLskf: Boolean) {
        val userAuthRequest = UserAuthPromptBuilder.requestUserAuth(this)
            .withTitle(getString(R.string.bio_auth_title))
            .withSubtitle(getSubtitle())
            .withDescription(formatEntryNames(viewModel.getEntryNames()))
            .withNegativeButton(getString(R.string.bio_auth_use_pin))
            .withSuccessCallback { authenticationSucceeded() }
            .withCancelledCallback { retryForcingPinUse() }
            .withFailureCallback { authenticationFailed() }
            .setForceLskf(forceLskf)
            .build()
        val cryptoObject = viewModel.getCryptoObject()
        userAuthRequest.authenticate(cryptoObject)
    }

    private fun retryForcingPinUse() {
        val runnable = { requestUserAuth(true) }
        // Without this delay, the prompt won't reshow
        Handler(Looper.getMainLooper()).postDelayed(runnable, 100)
    }

    private fun getSubtitle(): String {
        return if (readerCommonName != "") {
            if (readerIsTrusted) {
                getString(R.string.bio_auth_verifier_trusted_with_name, readerCommonName)
            } else {
                getString(R.string.bio_auth_verifier_untrusted_with_name, readerCommonName)
            }
        } else {
            getString(R.string.bio_auth_verifier_anonymous)
        }
    }

    private fun formatEntryNames(documents: Map<Document, List<String>>): String {
        val stringBuffer = StringBuffer()
        documents.forEach { (doc, entryNames) ->
            stringBuffer.append("Document: ${doc.userVisibleName}\n")
            entryNames.forEach {
                val stringId = resources.getIdentifier(it, "string", requireContext().packageName)
                val entryName = if (stringId != 0) getString(stringId) else it
                stringBuffer.append("• $entryName \n")
            }
            stringBuffer.append("\n")
        }
        return stringBuffer.toString()
    }

    // Called when user consent is canceled or failed to provide biometric authentication
    private fun authenticationFailed() {
        runOnUiThread {
            val message = getString(R.string.bio_auth_failed)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        onCloseConnection(
            sendSessionTerminationMessage = true,
            useTransportSpecificSessionTermination = false
        )
    }

    // Called when user gives consent to transfer
    private fun authenticationSucceeded() {
        // Send response again after user biometric authentication
        try {
            if (viewModel.sendResponse()) {
                // If this returns non-null it means that user-auth is still required.
                //
                // Wait, what? We just authenticated, so how can this happen?
                //
                // The answer is that it can happen when using face authentication b/c the
                // countdown to when the key is no longer considered unlocked starts when the
                // face is authenticated, NOT when the user presses the "Confirm" button. So
                // if an ACP is configured with say a five second timeout this can happen if
                // it takes more than five seconds for the user to press "Confirm"
                //
                val message = "Auth took too long, try again"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                retryForcingPinUse()
            }
        } catch (e: Exception) {
            val message = "Send response error: ${e.message}"
            Log.e(LOG_TAG, message, e)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            binding.txtConnectionStatus.append("\n$message")
        }
    }

    private var onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            onDone()
        }
    }

    fun onCloseConnection(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {
        viewModel.cancelPresentation(
            sendSessionTerminationMessage,
            useTransportSpecificSessionTermination
        )
        hideButtons()
    }

    fun onDone() {
        val destination = TransferDocumentFragmentDirections
            .actionTransferDocumentFragmentToSelectDocumentFragment()
        findNavController().navigate(destination)
    }
}