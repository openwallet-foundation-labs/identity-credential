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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.databinding.FragmentTransferDocumentBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.KeysAndCertificates
import com.android.mdl.app.readerauth.SimpleReaderTrustStore
import com.android.mdl.app.util.TransferStatus
import com.android.mdl.app.viewmodel.TransferDocumentViewModel
import org.jetbrains.anko.support.v4.runOnUiThread
import java.util.concurrent.Executor


class TransferDocumentFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "TransferDocumentFragment"
    }

    private var _binding: FragmentTransferDocumentBinding? = null
    private lateinit var vm: TransferDocumentViewModel
    private var readerCommonName = ""
    private var readerIsTrusted: Boolean = false

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferDocumentBinding.inflate(inflater)
        vm = ViewModelProvider(this).get(TransferDocumentViewModel::class.java)

        binding.fragment = this
        binding.vm = vm

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.getTransferStatus().observe(viewLifecycleOwner, { transferStatus ->
            when (transferStatus) {
                TransferStatus.ENGAGEMENT_READY -> {
                    Log.d(LOG_TAG, "Engagement Ready")
                }
                TransferStatus.CONNECTED -> {
                    Log.d(LOG_TAG, "Connected")
                }
                TransferStatus.REQUEST -> {
                    Log.d(LOG_TAG, "Request")
                    // TODO: Add option to the user select the which document share when there
                    //  are more than one for now we are just returning the first document we found
                    try {
                        val trustStore = SimpleReaderTrustStore(
                            KeysAndCertificates.getTrustedReaderCertificates(requireContext())
                        )
                        val requestedDocuments = vm.getRequestedDocuments()
                        requestedDocuments.forEach { reqDoc ->
                            val doc = vm.getDocuments().find { reqDoc.docType == it.docType }
                            if (reqDoc.readerAuth != null && reqDoc.readerAuthenticated) {
                                val readerChain = reqDoc.readerCertificateChain
                                if (readerChain != null) {
                                    val trustPath =
                                        trustStore.createCertificationTrustPath(readerChain.toList())

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
                                binding.txtDocuments.append("- ${doc?.userVisibleName} (${doc?.docType})\n")
                            } else {
                                binding.txtDocuments.append("- No document found for ${reqDoc.docType}\n")
                            }
                        }

                        // Ask for user consent and send response
                        sendResponseWithConsent(false)
                    } catch (e: Exception) {
                        val message = "On request received error: ${e.message}"
                        Log.e(LOG_TAG, message, e)
                        Toast.makeText(
                            requireContext(), message,
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.txtConnectionStatus.append("\n$message")
                    }
                }
                TransferStatus.DISCONNECTED -> {
                    Log.d(LOG_TAG, "Disconnected")
                    onCloseConnection(
                        sendSessionTerminationMessage = true,
                        useTransportSpecificSessionTermination = false
                    )
                }
                TransferStatus.ERROR -> {
                    Toast.makeText(
                        requireContext(), "An error occurred.",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(
                        TransferDocumentFragmentDirections.actionTransferDocumentFragmentToSelectDocumentFragment()
                    )
                }
            }
        }
        )
    }

    private val executor = Executor {
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }

        it.run()
    }

    private fun sendResponseWithConsent(forceLskf : Boolean) {
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.bio_auth_title))
            .setSubtitle(getSubtitle())
            .setDescription(formatEntryNames(vm.getEntryNames()))

        if (forceLskf) {
            // TODO: this works only on Android 11 or later but for now this is fine
            //   as this is just a reference/test app and this path is only hit if
            //   the user actually presses the "Use PIN" button.  Longer term, we should
            //   fall back to using KeyGuard which will work on all Android versions.
            promptInfoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            if (BiometricManager.from(requireContext()).canAuthenticate(BIOMETRIC_STRONG)
                == BIOMETRIC_SUCCESS
            ) {
                promptInfoBuilder.setNegativeButtonText(getString(R.string.bio_auth_use_pin))
            } else {
                // No biometrics enrolled, force use of LSKF
                promptInfoBuilder.setDeviceCredentialAllowed(true)
            }
        }

        val biometricPrompt = BiometricPrompt(this, executor, biometricAuthCallback)
        val cryptoObject = vm.getCryptoObject()
        val promptInfo = promptInfoBuilder.build()
        if (cryptoObject != null) {
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } else {
            biometricPrompt.authenticate(promptInfo)
        }
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
        val sb = StringBuffer()
        documents.forEach { (doc, entryNames) ->
            sb.append("Document: ${doc.userVisibleName}\n")
            entryNames.forEach {
                val stringId = resources.getIdentifier(it, "string", requireContext().packageName)
                val entryName = if (stringId != 0) {
                    getString(stringId)
                } else {
                    it
                }
                sb.append("• $entryName \n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    // Called when user consent is canceled or failed to provide biometric authentication
    private fun authenticationFailed() {
        runOnUiThread {
            Toast.makeText(
                requireContext(), getString(R.string.bio_auth_failed),
                Toast.LENGTH_SHORT
            ).show()
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
            if (vm.sendResponse() != null) {
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
                Toast.makeText(
                    requireContext(), "Auth took too long, try again",
                    Toast.LENGTH_SHORT
                ).show()
                // Without this delay, the prompt won't reshow (bug in BiometricPrompt).
                Handler().postDelayed({
                    sendResponseWithConsent(false)
                }, 100)
            }
        } catch (e: Exception) {
            val message = "Send response error: ${e.message}"
            Log.e(LOG_TAG, message, e)
            Toast.makeText(
                requireContext(), message,
                Toast.LENGTH_SHORT
            ).show()
            binding.txtConnectionStatus.append("\n$message")
        }
    }

    private val biometricAuthCallback = object : BiometricPrompt.AuthenticationCallback() {

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // reached max attempts to authenticate the user, or authentication dialog was cancelled

            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                // Without this delay, the prompt won't reshow
                Handler().postDelayed({
                    sendResponseWithConsent(true)
                }, 100)
            } else {
                Log.d(
                    LOG_TAG,
                    "Attempt to authenticate the user has failed $errorCode - $errString"
                )
                authenticationFailed()
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            Log.d(LOG_TAG, "User authentication succeeded")
            authenticationSucceeded()
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()

            Log.d(LOG_TAG, "Attempt to authenticate the user has failed")
        }
    }

    var callback = object : OnBackPressedCallback(true /* enabled by default */) {
        override fun handleOnBackPressed() {
            onDone()
        }
    }

    fun onCloseConnection(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {
        vm.cancelPresentation(sendSessionTerminationMessage, useTransportSpecificSessionTermination)
        binding.txtConnectionStatus.text = getString(R.string.connection_mdoc_closed)
        binding.btCloseConnection.visibility = View.GONE
        binding.btCloseTerminationMessage.visibility = View.GONE
        binding.btCloseTransportSpecific.visibility = View.GONE
        binding.btOk.visibility = View.VISIBLE
    }

    fun onDone() {
        findNavController().navigate(
            TransferDocumentFragmentDirections.actionTransferDocumentFragmentToSelectDocumentFragment()
        )
    }
}