package com.android.identity_credential.wallet.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity_credential.wallet.ui.destination.consentprompt.ConsentPrompt
import com.android.identity_credential.wallet.ui.destination.consentprompt.ConsentPromptData
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Consent Prompt dialog that extends BottomSheetDialogFragment shows up as an overlay above the
 * current UI via composition. Defines and expects a PromptResponseListener to be provided for
 * notifying when user taps on Confirm or Cancel.
 *
 * It is expected that this dialog be instantiated, have a listener be provided, then execute the
 * built-in function [show].
 *
 * @param consentPromptData data that is extracted (via TransferHelper) during a presentation engagement
 * @param documentTypeRepository repository used to get the human-readable credential names
 */
class ConsentPromptDialog(
    val consentPromptData: ConsentPromptData,
    val documentTypeRepository: DocumentTypeRepository
) : BottomSheetDialogFragment() {

    /**
     * Listener notifying when user taps on Confirm or Cancel.
     */
    interface PromptResponseListener {
        fun onConfirm()
        fun onCancel()
    }

    /**
     * Instance var of the listener that is expected to be set via the setter function [setResponseListener]
     * else throws an Exception when user taps on Confirm or Cancel.
     */
    private lateinit var promptResponseListener: PromptResponseListener

    /**
     * Function that enforces a "lateinit" of the listener var.
     */
    fun setResponseListener(listener: PromptResponseListener) {
        promptResponseListener = listener
    }

    /**
     * Define the ConsentPrompt composition.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        ComposeView(requireContext()).apply {
            setContent {
                IdentityCredentialTheme {
                    ConsentPrompt(
                        consentData = consentPromptData,
                        documentTypeRepository = documentTypeRepository,
                        onConfirm = { // user accepted to send requested credential data
                            promptResponseListener.onConfirm()
                        },
                        onCancel = { // user declined submitting data to requesting party
                            promptResponseListener.onCancel()
                        }
                    )
                }
            }
        }
}