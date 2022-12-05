package com.android.mdl.app.authconfirmation

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.android.mdl.app.util.DocumentData
import com.android.mdl.app.util.log
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
        val elementsToSign = viewModel.requestedElements()
        val sheetData = mapToConfirmationSheetData(elementsToSign)
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
                        onElementToggled = { element -> viewModel.toggleSignedElement(element) },
                        onConfirm = { sendResponse() },
                        onCancel = {
                            dismiss()
                            cancelAuthorization()
                        }
                    )
                }
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        cancelAuthorization()
    }

    private fun cancelAuthorization() {
        viewModel.onAuthenticationCancelled()
    }

    private fun mapToConfirmationSheetData(
        elementsToSign: List<RequestedDocumentData>
    ): List<ConfirmationSheetData> {
        return elementsToSign.map { documentData ->
            viewModel.addDocumentForSigning(documentData)
            val elements = documentData.requestedElements.map { element ->
                viewModel.toggleSignedElement(element)
                val displayName = stringValueFor(element.namespace, element.value)
                ConfirmationSheetData.DocumentElement(displayName, element)
            }
            ConfirmationSheetData(documentData.userReadableName, elements)
        }
    }

    private fun stringValueFor(namespace: String, element: String): String {
        val requested = if (element == "portrait") portraitFor(namespace) else element
        val identifier = resources.getIdentifier(requested, "string", requireContext().packageName)
        return if (identifier != 0) getString(identifier) else element
    }

    private fun portraitFor(namespace: String): String {
        return if (namespace == DocumentData.EU_PID_NAMESPACE) "facial_portrait" else "portrait"
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
            log(message, e)
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
}