package com.android.mdl.app.authconfirmation

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.app.R
import com.android.mdl.app.authprompt.UserAuthPromptBuilder
import com.android.mdl.app.theme.HolderAppTheme
import com.android.mdl.app.viewmodel.TransferDocumentViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AuthConfirmationFragment : BottomSheetDialogFragment() {

    private val viewModel: TransferDocumentViewModel by activityViewModels()
    private val arguments by navArgs<AuthConfirmationFragmentArgs>()
    private var isSendingInProgress = mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val propertiesToSign = viewModel.requestedProperties()
        val sheetData = mapToConfirmationSheetData(propertiesToSign)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HolderAppTheme {
                    ConfirmationSheet(
                        modifier = Modifier.fillMaxWidth(),
                        title = getSubtitle(),
                        isTrustedReader = arguments.readerIsTrusted,
                        isSendingInProgress = isSendingInProgress.value,
                        sheetData = sheetData,
                        onPropertyToggled = { namespace, property ->
                            viewModel.toggleSignedProperty(namespace, property)
                        },
                        onConfirm = { sendResponse() },
                        onCancel = { dismiss() }
                    )
                }
            }
        }
    }

    private fun mapToConfirmationSheetData(
        propertiesToSign: List<RequestedDocumentData>
    ): List<ConfirmationSheetData> {
        return propertiesToSign.map { documentData ->
            viewModel.addDocumentForSigning(documentData)
            val properties = documentData.requestedProperties.map { property ->
                viewModel.toggleSignedProperty(documentData.namespace, property)
                val displayName = stringValueFor(property)
                ConfirmationSheetData.DocumentProperty(displayName, property)
            }
            ConfirmationSheetData(documentData.nameTypeTitle(), documentData.namespace, properties)
        }
    }

    private fun stringValueFor(property: String): String {
        val identifier = resources.getIdentifier(property, "string", requireContext().packageName)
        return if (identifier != 0) getString(identifier) else property
    }

    private fun sendResponse() {
        isSendingInProgress.value = true
        // Will return false if authentication is needed
        if (!viewModel.sendResponseForSelection()) {
            requestUserAuth(false)
        } else {
            findNavController().navigateUp()
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