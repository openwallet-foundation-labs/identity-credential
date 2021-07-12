package com.android.mdl.app.fragment

import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.CryptoObject
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.security.identity.InvalidRequestMessageException
import com.android.mdl.app.R
import com.android.mdl.app.databinding.FragmentTransferDocumentBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.fragment.TransferDocumentFragmentDirections.Companion.actionTransferDocumentFragmentToDocumentSharedFragment
import com.android.mdl.app.fragment.TransferDocumentFragmentDirections.Companion.actionTransferDocumentFragmentToSelectDocumentFragment
import com.android.mdl.app.util.TransferStatus
import com.android.mdl.app.viewmodel.TransferDocumentViewModel
import org.jetbrains.anko.support.v4.runOnUiThread
import java.util.concurrent.Executor


class TransferDocumentFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "TransferDocumentFragment"
    }

    private val args: TransferDocumentFragmentArgs by navArgs()
    private lateinit var document: Document

    private var _binding: FragmentTransferDocumentBinding? = null
    private lateinit var vm: TransferDocumentViewModel

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        document = args.document

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferDocumentBinding.inflate(inflater)
        vm = ViewModelProvider(this).get(TransferDocumentViewModel::class.java)

        binding.fragment = this

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
                }
                TransferStatus.DISCONNECTED -> {
                    findNavController().navigate(
                        actionTransferDocumentFragmentToDocumentSharedFragment()
                    )
                }
                TransferStatus.ERROR -> {
                    Toast.makeText(
                        requireContext(), "An error occurred.",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(
                        actionTransferDocumentFragmentToSelectDocumentFragment()
                    )
                }
            }
        }
        )

        try {
            val cryptoObject = vm.sendResponse()
            cryptoObject?.let {
                // Biometric authentication required
                showBiometricPrompt(it)
            }
        } catch (e: InvalidRequestMessageException) {
            Log.e(LOG_TAG, "Send response error: ${e.message}")
            Toast.makeText(
                requireContext(), "Send response error: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }

    }

    private val executor = Executor {
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }

        it.run()
    }

    private fun showBiometricPrompt(cryptoObject: CryptoObject) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.bio_auth_title))
            .setSubtitle(getString(R.string.bio_auth_subtitle))
            .setNegativeButtonText(getString(R.string.bio_auth_cancel))
            .build()

        val biometricPrompt = BiometricPrompt(this, executor, biometricAuthCallback)

        // Displays the "log in" prompt.
        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }

    private val biometricAuthCallback = object : BiometricPrompt.AuthenticationCallback() {

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // reached max attempts to authenticate the user, or authentication dialog was cancelled

            Log.d(LOG_TAG, "Attempt to authenticate the user has failed")

            runOnUiThread {
                Toast.makeText(requireContext(), getString(R.string.bio_auth_failed),
                    Toast.LENGTH_SHORT).show()
            }
            onDone()
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)

            // Send response again after user biometric authentication
            vm.sendResponse()
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

    fun onDone() {
        vm.cancelPresentation()
        findNavController().navigate(
            actionTransferDocumentFragmentToSelectDocumentFragment()
        )
    }
}