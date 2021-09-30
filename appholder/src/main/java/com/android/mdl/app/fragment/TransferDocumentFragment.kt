package com.android.mdl.app.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.security.identity.InvalidRequestMessageException
import com.android.mdl.app.R
import com.android.mdl.app.databinding.FragmentTransferDocumentBinding
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

        vm.getTransferStatus().observe(viewLifecycleOwner, {
            when (it) {
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
                    val requestedDocuments = vm.getRequestedDocuments()
                    requestedDocuments.forEach { doc ->
                        binding.txtDocuments.append("- ${doc.userVisibleName} (${doc.docType})\n")
                    }

                    try {
                        // Ask for user consent and send response
                        sendResponseWithConsent()
                    } catch (e: InvalidRequestMessageException) {
                        Log.e(LOG_TAG, "Send response error: ${e.message}")
                        Toast.makeText(
                            requireContext(), "Send response error: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                TransferStatus.DISCONNECTED -> {
                    Log.d(LOG_TAG, "Disconnected")
                    binding.txtConnectionStatus.text = getString(R.string.connection_mdoc_closed)
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

    private fun sendResponseWithConsent() {
        // Ask for biometric authentication when the device has biometric enrolled,
        // otherwise just ask for consent in a dialog
        if (BiometricManager.from(requireContext())
                .canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS
        ) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.bio_auth_title))
                .setSubtitle(getString(R.string.bio_auth_subtitle))
                .setDescription(formatEntryNames(vm.getEntryNames()))
                .setAllowedAuthenticators(DEVICE_CREDENTIAL)
                .build()

            val biometricPrompt = BiometricPrompt(this, executor, biometricAuthCallback)

            val cryptoObject = vm.getCryptoObject()
            // Displays the "log in" prompt.
            if (cryptoObject != null) {
                biometricPrompt.authenticate(promptInfo, cryptoObject)
            } else {
                biometricPrompt.authenticate(promptInfo)
            }
        } else {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(requireActivity())
            builder.setTitle(getString(R.string.bio_auth_title))
            builder.setMessage("${getString(R.string.bio_auth_subtitle)} \n${formatEntryNames(vm.getEntryNames())}")
            builder.setPositiveButton(R.string.bt_ok) { _, _ ->
                authenticationSucceeded()
            }
            builder.setNegativeButton(R.string.bt_cancel) { _, _ ->
                authenticationFailed()
            }
            builder.create()
            builder.show()
        }
    }

    private fun formatEntryNames(entryNames: List<String>): String {
        val sb = StringBuffer()
        entryNames.forEach {
            val stringId = resources.getIdentifier(it, "string", requireContext().packageName)
            val entryName = if (stringId != 0) {
                getString(stringId)
            } else {
                it
            }
            sb.append("• $entryName \n")
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
        vm.cancelPresentation()
        binding.txtConnectionStatus.text = getString(R.string.connection_mdoc_closed)
    }

    // Called when user gives consent to transfer
    private fun authenticationSucceeded() {
        // Send response again after user biometric authentication
        vm.sendResponse()
    }

    private val biometricAuthCallback = object : BiometricPrompt.AuthenticationCallback() {

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // reached max attempts to authenticate the user, or authentication dialog was cancelled

            Log.d(LOG_TAG, "Attempt to authenticate the user has failed $errorCode - $errString")

            authenticationFailed()
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            Log.d(LOG_TAG, "User authentication succeeded")
            authenticationSucceeded()
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()

            Log.d(LOG_TAG, "Attempt to authenticate the user has failed")
            authenticationFailed()
        }
    }

    var callback = object : OnBackPressedCallback(true /* enabled by default */) {
        override fun handleOnBackPressed() {
            onDone()
        }
    }

    fun onDone() {
        vm.cancelPresentation()
        findNavController().navigate(
            TransferDocumentFragmentDirections.actionTransferDocumentFragmentToSelectDocumentFragment()
        )
    }
}