package com.android.identity_credential.wallet.ui.prompt.consent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity_credential.wallet.presentation.PresentationRequestData
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Show the Consent prompt
 *
 * Async extension function that renders the Consent Prompt (Composable) from a Dialog Fragment.
 * Returns a [Boolean] identifying that user tapped on Confirm or Cancel button.
 *
 * @param activity the [FragmentActivity] to show the Dialog Fragment via Activity's FragmentManager
 * @param presentationRequestData contains data after parsing the device request bytes.
 * @param documentTypeRepository the repository containing user-facing credential names
 * @return a [Boolean] indicating whether the user tapped on the 'Confirm' or 'Cancel' button.
 */
suspend fun showConsentPrompt(
    activity: FragmentActivity,
    presentationRequestData: PresentationRequestData,
    documentTypeRepository: DocumentTypeRepository,
): Boolean =
    suspendCancellableCoroutine { continuation ->
        // new instance of the ConsentPrompt bottom sheet dialog fragment but not shown yet
        val consentPrompt = ConsentPrompt(
            consentPromptEntryFieldData = ConsentPromptEntryFieldData(
                credentialId = presentationRequestData.document.name,
                documentName = presentationRequestData.document.documentConfiguration.displayName,
                credentialData = presentationRequestData.document.documentConfiguration.mdocConfiguration!!.staticData,
                documentRequest = presentationRequestData.documentRequest,
                docType = presentationRequestData.docType,
                verifier = presentationRequestData.trustPoint,
            ),
            documentTypeRepository = documentTypeRepository,
            onConsentPromptResult = { promptWasSuccessful ->
                continuation.resume(promptWasSuccessful)
            }
        )
        // show the consent prompt fragment
        consentPrompt.show(activity.supportFragmentManager, "consent_prompt")
    }


/**
 * ConsentPrompt Dialog Fragment class that renders Consent prompt via Composition for the purposes
 * of running Consent Prompt synchronously and return the prompt's result as a [Boolean] rather than
 * deal with callback hell.
 *
 * Extends [BottomSheetDialogFragment] and shows up as an overlay above the current UI.
 *
 * Expects a [ConsentPromptResponseListener] instance to be provided to notify when the user taps on
 * Confirm or Cancel.
 *
 * @param consentPromptEntryFieldData data that is extracted (via TransferHelper) during a presentation engagement
 * @param documentTypeRepository repository used to get the human-readable credential names
 * @param onConsentPromptResult callback to notify with the result of the prompt with a [Boolean]
                  depending on whether the 'Confirm' [true] or 'Cancel' [false] button was tapped.
 * @extends [BottomSheetDialogFragment] that can create the Fragment's contents via Composition.
 */
private class ConsentPrompt(
    private val consentPromptEntryFieldData: ConsentPromptEntryFieldData,
    private val documentTypeRepository: DocumentTypeRepository,
    private val onConsentPromptResult: (Boolean) -> Unit,
) : BottomSheetDialogFragment() {
    /**
     * Define the composable [ConsentPromptEntryField] and issue callbacks to [onConsentPromptResult]
     * based on which button is tapped.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        ComposeView(requireContext()).apply {
            setContent {
                IdentityCredentialTheme {
                    // define the ConsentPromptComposable (and show)
                    ConsentPromptEntryField(
                        consentData = consentPromptEntryFieldData,
                        documentTypeRepository = documentTypeRepository,
                        // user accepted to send requested credential data
                        onConfirm = {
                            // notify that the user tapped on the 'Confirm' button
                            onConsentPromptResult.invoke(true)
                            dismiss()
                        },
                        // user declined submitting data to requesting party
                        onCancel = {
                            // notify that the user tapped on the 'Cancel' button
                            onConsentPromptResult.invoke(false)
                            dismiss()
                        }
                    )
                }
            }
        }
}