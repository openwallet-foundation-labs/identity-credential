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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.android.mdl.app.R
import com.android.mdl.app.authprompt.UserAuthPromptBuilder
import com.android.mdl.app.databinding.FragmentAuthConfirmationBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.viewmodel.TransferDocumentViewModel

class AuthConfirmationFragment : Fragment() {

    private var _binding: FragmentAuthConfirmationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TransferDocumentViewModel by viewModels()

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
//        val requestedDocTypes = viewModel.getRequestedDocuments().map { it.docType }
//        val requestedDocs = viewModel.getDocuments().filter { it.docType in requestedDocTypes }
//        val transferManager = TransferManager.getInstance(requireContext())
//        val propertiesToSign = mutableMapOf<Document, MutableList<String>>()
//        requestedDocs.forEach { document ->
//            transferManager.readDocumentEntries(document)?.let { entries ->
//                val fieldsNeedingAuth = mutableListOf<String>()
//                entries.namespaces.forEach { namespace ->
//                    entries.getEntryNames(namespace).forEach { entry ->
//                        if(entries.getStatus(namespace, entry) == CredentialDataResult.Entries.STATUS_USER_AUTHENTICATION_FAILED) {
//                            fieldsNeedingAuth.add(entry)
//                        }
//                    }
//                }
//                propertiesToSign[document] = fieldsNeedingAuth
//            }
//        }
        val propertiesToSign = viewModel.getEntryNames()
        propertiesToSign.forEach { (document, properties) ->
            binding.llPropertiesContainer.addView(documentNameFor(document))
            properties.forEach { property ->
                binding.llPropertiesContainer.addView(switchFor(property))
            }
            binding.llPropertiesContainer.addView(spacer())
        }
        binding.btnConfirm.setOnClickListener { requestUserAuth(false) }
    }

    private fun documentNameFor(document: Document): View {
        return TextView(requireContext()).apply {
            val value = "${document.userVisibleName}  |  ${document.docType}"
            text = value
            textSize = 16f
        }
    }

    private fun switchFor(property: String): View {
        return Switch(requireContext()).apply {
            text = property
            isChecked = true
            setPadding(24, 0, 24, 0)
        }
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
            if (viewModel.sendResponse()) {
                val message = "Auth took too long, try again"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                retryForcingPinUse()
            }
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
        TODO()
    }

    private companion object {
        private const val LOG_TAG = "AuthConfirmationFragment"
    }
}