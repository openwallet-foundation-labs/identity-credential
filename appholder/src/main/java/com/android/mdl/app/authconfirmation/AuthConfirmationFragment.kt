package com.android.mdl.app.authconfirmation

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.authprompt.UserAuthPromptBuilder
import com.android.mdl.app.databinding.FragmentAuthConfirmationBinding
import com.android.mdl.app.viewmodel.TransferDocumentViewModel
import com.google.android.material.switchmaterial.SwitchMaterial

class AuthConfirmationFragment : Fragment() {

    private var _binding: FragmentAuthConfirmationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TransferDocumentViewModel by activityViewModels()

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

    private val signedProperties = MyDataStructure()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val propertiesToSign = viewModel.requested
        propertiesToSign.forEach { documentData ->
            binding.llPropertiesContainer.addView(documentNameFor(documentData))
            documentData.requestedProperties.forEach { property ->
                binding.llPropertiesContainer.addView(switchFor(documentData.namespace, property))
            }
            binding.llPropertiesContainer.addView(spacer())
        }
        binding.btnConfirm.setOnClickListener { requestUserAuth(false) }
    }

    private fun documentNameFor(document: RequestedDocumentData): View {
        signedProperties.addNamespace(document)
        return TextView(requireContext()).apply {
            text = document.nameTypeTitle()
            textSize = 16f
        }
    }

    private fun switchFor(namespace: String, property: String): View {
        signedProperties.toggleProperty(namespace, property)
        val switch = SwitchMaterial(requireContext()).apply {
            val identifier = resources.getIdentifier(property, "string", context.packageName)
            text = if (identifier != 0) getString(identifier) else property
            isChecked = true
            setPadding(24, 0, 24, 0)
        }
        switch.setOnCheckedChangeListener { _, _ ->
            signedProperties.toggleProperty(namespace, property)
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

    private fun authenticationSucceeded() {
        try {
//            if (viewModel.sendResponse()) {
//                val message = "Auth took too long, try again"
//                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
//                retryForcingPinUse()
//            }
            viewModel.sendResponseForSelection(signedProperties.collect())
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
        //TODO close connection (call here or call in the prev fragment)
    }

    private companion object {
        private const val LOG_TAG = "AuthConfirmationFragment"
    }
}