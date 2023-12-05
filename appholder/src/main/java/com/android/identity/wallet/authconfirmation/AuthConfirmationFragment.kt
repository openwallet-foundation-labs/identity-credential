package com.android.identity.wallet.authconfirmation

import android.content.DialogInterface
import android.os.Bundle
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
import com.android.identity.wallet.HolderApp
import com.android.identity.wallet.R
import com.android.identity.wallet.support.SecureAreaSupport
import com.android.identity.wallet.theme.HolderAppTheme
import com.android.identity.wallet.transfer.AddDocumentToResponseResult
import com.android.identity.wallet.viewmodel.TransferDocumentViewModel
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
                val displayName = stringValueFor(
                    documentData.requestedDocument.docType,
                    element.namespace,
                    element.value
                )
                ConfirmationSheetData.DocumentElement(displayName, element)
            }
            ConfirmationSheetData(documentData.userReadableName, elements)
        }
    }

    private fun stringValueFor(docType: String, namespace: String, element: String): String {
        val credentialType = HolderApp.credentialTypeRepositoryInstance.getCredentialTypes()
            .find { it.mdocCredentialType != null && it.mdocCredentialType?.docType == docType }
            ?: return element
        val mdocNamespace = credentialType.mdocCredentialType?.namespaces?.find { it.namespace == namespace }
            ?: return element
        val dataElement = mdocNamespace.dataElements.find { it.attribute.identifier == element }
            ?: return element
        return dataElement.attribute.displayName
    }

    private fun sendResponse() {
        isSendingInProgress.value = true
        viewModel.sendResponseForSelection(
            onResultReady = {
                onSendResponseResult(it)
            })
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

    private fun onSendResponseResult(result: AddDocumentToResponseResult) {
        when (result) {
            is AddDocumentToResponseResult.DocumentLocked -> {

                val secureAreaSupport = SecureAreaSupport.getInstance(
                    requireContext(),
                    result.authKey.secureArea
                )
                with(secureAreaSupport) {
                    unlockKey(
                        authKey = result.authKey,
                        onKeyUnlocked = { keyUnlockData ->
                            viewModel.sendResponseForSelection(
                                onResultReady = {
                                    onSendResponseResult(it)
                                },
                                result.authKey,
                                keyUnlockData
                            )
                        },
                        onUnlockFailure = { wasCancelled ->
                            if (wasCancelled) {
                                cancelAuthorization()
                            } else {
                                viewModel.closeConnection()
                            }
                        }
                    )
                }
            }

            is AddDocumentToResponseResult.DocumentAdded -> {
                if (result.signingKeyUsageLimitPassed) {
                    toast("Using previously used Auth Key")
                }
                findNavController().navigateUp()
            }
        }
    }

    private fun toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(requireContext(), message, duration).show()
    }
}