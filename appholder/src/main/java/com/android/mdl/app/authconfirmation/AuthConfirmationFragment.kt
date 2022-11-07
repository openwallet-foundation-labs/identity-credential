package com.android.mdl.app.authconfirmation

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.app.R
import com.android.mdl.app.authprompt.UserAuthPromptBuilder
import com.android.mdl.app.databinding.FragmentAuthConfirmationBinding
import com.android.mdl.app.viewmodel.TransferDocumentViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class AuthConfirmationFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAuthConfirmationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TransferDocumentViewModel by activityViewModels()
    private val arguments by navArgs<AuthConfirmationFragmentArgs>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthConfirmationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvTitle.text = getSubtitle()
        val propertiesToSign = viewModel.requestedProperties()
        propertiesToSign.forEach { documentData ->
            binding.llPropertiesContainer.addView(documentNameFor(documentData))
            documentData.requestedProperties.forEach { property ->
                binding.llPropertiesContainer.addView(switchFor(documentData.namespace, property))
            }
            binding.llPropertiesContainer.addView(spacer())
        }
        binding.llPropertiesContainer.addView(confirmationButton())
    }

    private fun confirmationButton(): View {
        val button = MaterialButton(requireContext()).apply {
            text = getString(R.string.btn_send_data)
            setPadding(16)
        }
        button.setOnClickListener { sendResponse() }
        return button
    }

    private fun sendResponse() {
        binding.loadingProgress.visibility = View.VISIBLE
        binding.llPropertiesContainer.visibility = View.GONE
        // Will return false if authentication is needed
        if (!viewModel.sendResponseForSelection()) {
            requestUserAuth(false)
        } else {
            findNavController().navigateUp()
        }
    }

    private fun documentNameFor(document: RequestedDocumentData): View {
        viewModel.addDocumentForSigning(document)
        return TextView(requireContext()).apply {
            text = document.nameTypeTitle()
            textSize = 16f
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun switchFor(namespace: String, property: String): View {
        viewModel.toggleSignedProperty(namespace, property)
        val switch = SwitchMaterial(requireContext()).apply {
            val identifier = resources.getIdentifier(property, "string", context.packageName)
            text = if (identifier != 0) getString(identifier) else property
            isChecked = true
            setPadding(24, 0, 24, 0)
        }
        switch.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleSignedProperty(namespace, property)
        }
        return switch
    }

    private fun spacer(): View {
        return Space(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, 24)
        }
    }

    private fun requestUserAuth(forceLskf: Boolean) {
        val userAuthRequest = UserAuthPromptBuilder.requestUserAuth(this)
            .withTitle(getString(R.string.bio_auth_title))
            .withNegativeButton(getString(R.string.bio_auth_use_pin))
            .withSuccessCallback { authenticationSucceeded() }
            .withCancelledCallback { retryForcingPinUse() }
            .withFailureCallback { authenticationFailed() }
            .setForceLskf(forceLskf)
            .build()
        val cryptoObject = viewModel.getCryptoObject()
        userAuthRequest.authenticate(cryptoObject)
    }

    private fun getSubtitle(): String {
        val readerCommonName = arguments.readerCommonName
        val readerIsTrusted = arguments.readerIsTrusted
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

    private fun authenticationSucceeded() {
        try {
            viewModel.sendResponseForSelection()
            findNavController().navigateUp()
        } catch (e: Exception) {
            val message = "Send response error: ${e.message}"
            Log.e(LOG_TAG, message, e)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun retryForcingPinUse() {
        val runnable = { requestUserAuth(true) }
        // Without this delay, the prompt won't reshow
        Handler(Looper.getMainLooper()).postDelayed(runnable, 100)
    }

    private fun authenticationFailed() {
        viewModel.closeConnection()
    }

    private companion object {
        private const val LOG_TAG = "AuthConfirmationFragment"
    }
}