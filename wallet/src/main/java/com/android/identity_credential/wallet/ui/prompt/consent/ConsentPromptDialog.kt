package com.android.identity_credential.wallet.ui.prompt.consent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentManager
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity_credential.wallet.presentation.PresentationRequestData
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Show the ConsentPrompt via the Dialog wrapper class and return a [Boolean] indicating that the
 * user confirmed or cancelled the credential request.
 */
suspend fun showConsentPrompt(
    presentationRequestData: PresentationRequestData,
    documentTypeRepository: DocumentTypeRepository,
    fragmentManager: FragmentManager
): Boolean =
    suspendCancellableCoroutine { continuation ->
        // new instance of the ConsentPrompt bottom sheet dialog fragment but not shown yet
        val consentPromptDialog = ConsentPromptDialog(
            consentPromptData = ConsentPromptData(
                credentialId = presentationRequestData.document.name,
                documentName = presentationRequestData.document.documentConfiguration.displayName,
                credentialData = presentationRequestData.document.documentConfiguration.mdocConfiguration!!.staticData,
                documentRequest = presentationRequestData.documentRequest,
                docType = presentationRequestData.docType,
                verifier = presentationRequestData.trustPoint,
            ),
            documentTypeRepository = documentTypeRepository,
            consentPromptListener = object :
                ConsentPromptDialog.ConsentPromptResponseListener {
                override fun onConfirm() {
                    continuation.resume(true)
                }

                override fun onCancel() {
                    continuation.resume(false)
                }
            }
        )
        consentPromptDialog.show(fragmentManager, "consent_prompt")
    }


/**
 * Wrapper (Dialog) class for rendering Consent prompt via composition since composable functions
 * cannot be defined as "suspend".
 *
 * Extends BottomSheetDialogFragment and shows up as an overlay above the current UI.
 *
 * Expects a [ConsentPromptResponseListener] instance to be provided to notify when the user taps on
 * Confirm or Cancel.
 *
 * @param consentPromptData data that is extracted (via TransferHelper) during a presentation engagement
 * @param documentTypeRepository repository used to get the human-readable credential names
 * @param consentPromptListener the listener that notifies when user taps on "Confirm" or "Cancel"
 */
class ConsentPromptDialog(
    private val consentPromptData: ConsentPromptData,
    private val documentTypeRepository: DocumentTypeRepository,
    private val consentPromptListener: ConsentPromptResponseListener
) : BottomSheetDialogFragment() {

    /**
     * Listener notifying when user taps on Confirm or Cancel.
     */
    interface ConsentPromptResponseListener {
        fun onConfirm()
        fun onCancel()
    }

    /**
     * Define the ConsentPrompt composition and issue callbacks based on user's actions.
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
                            consentPromptListener.onConfirm()
                            dismiss()
                        },
                        onCancel = { // user declined submitting data to requesting party
                            consentPromptListener.onCancel()
                            dismiss()
                        }
                    )
                }
            }
        }
}