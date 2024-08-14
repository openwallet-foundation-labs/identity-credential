package com.android.identity_credential.wallet.ui.prompt.consent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.document.Document
import com.android.identity.document.DocumentRequest
import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.sdjwt.SdJwtVerifiableCredential
import com.android.identity.sdjwt.credential.SdJwtVcCredential
import com.android.identity.sdjwt.vc.JwtBody
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Show the Consent prompt
 *
 * Async function that renders the Consent Prompt (Composable) from a Dialog Fragment.
 * Returns a [Boolean] identifying that user tapped on Confirm or Cancel button.
 *
 * @param activity the [FragmentActivity] to show the Dialog Fragment via Activity's FragmentManager
 * @param presentationRequestData contains data after parsing the device request bytes.
 * @param documentTypeRepository the repository containing user-facing credential names
 * @return a [Boolean] indicating whether the user tapped on the 'Confirm' or 'Cancel' button.
 */
suspend fun showConsentPrompt(
    activity: FragmentActivity,
    documentTypeRepository: DocumentTypeRepository,
    credential: SecureAreaBoundCredential,
    document: Document,
    documentRequest: DocumentRequest,
    trustPoint: TrustPoint?
): Boolean =
    suspendCancellableCoroutine { continuation ->
        var credentialFormat = when(credential) {
            is MdocCredential -> CredentialFormat.MDOC_MSO
            is SdJwtVcCredential -> CredentialFormat.SD_JWT_VC
            else -> throw IllegalArgumentException("Unknown credential type")
        }
        val staticData = getStaticData(document, credential)

        // new instance of the ConsentPrompt bottom sheet dialog fragment but not shown yet
        val consentPrompt = ConsentPrompt(
            consentPromptEntryFieldData = ConsentPromptEntryFieldData(
                credentialId = document.name,
                documentName = document.documentConfiguration.displayName,
                credentialData = staticData,
                credentialFormat = credentialFormat,
                documentRequest = documentRequest,
                docType = document.documentConfiguration.mdocConfiguration?.docType,
                verifier = trustPoint,
            ),
            documentTypeRepository = documentTypeRepository,
            onConsentPromptResult = { promptWasSuccessful ->
                continuation.resume(promptWasSuccessful)
            }
        )
        // show the consent prompt fragment
        consentPrompt.show(activity.supportFragmentManager, "consent_prompt")
    }

private fun getStaticData(document: Document, credential: SecureAreaBoundCredential): NameSpacedData {
    if (credential is MdocCredential) {
        Logger.i("ConsentPrompt", "Getting static data for mdoc credential.")
        return document.documentConfiguration.mdocConfiguration!!.staticData
    }

    Logger.i("ConsentPrompt", "Getting static data for SD-JWT credential.")
    // This NameSpacedData is only used to display which attributes are being requested, and the
    // value stored in the ByteArray is never used. Until we have a cleaner way to surface these
    // attribute names, we're just filling these entries and leaving the ByteArray empty.
    val namespace = "credentialSubject"
    val builder = NameSpacedData.Builder()
        .putEntry(namespace, "iss", ByteArray(0))
        .putEntry(namespace, "vct", ByteArray(0))

    val sdJwt = SdJwtVerifiableCredential.fromString(
        String(credential.issuerProvidedData, Charsets.US_ASCII))
    for (disclosure in sdJwt.disclosures) {
        builder.putEntry(namespace, disclosure.key, ByteArray(0))
    }

    val bodyObj = JwtBody.fromString(sdJwt.body)
    if (bodyObj.timeSigned != null) builder.putEntry(namespace, "iat", ByteArray(0))
    if (bodyObj.timeValidityBegin != null) builder.putEntry(namespace, "nbf", ByteArray(0))
    if (bodyObj.timeValidityEnd != null) builder.putEntry(namespace, "exp", ByteArray(0))
    if (bodyObj.publicKey != null) builder.putEntry(namespace, "cnf", ByteArray(0))
    return builder.build()
}

/**
 * ConsentPrompt Dialog Fragment class that renders Consent prompt via Composition for the purposes
 * of running Consent Prompt synchronously and return the prompt's result as a [Boolean] rather than
 * deal with callback hell.
 *
 * Extends [BottomSheetDialogFragment] and shows up as an overlay above the current UI.
 *
 * @param consentPromptEntryFieldData data that is extracted (via TransferHelper) during a presentation engagement
 * @param documentTypeRepository repository used to get the human-readable credential names
 * @param onConsentPromptResult callback to notify with the result of the prompt with a [Boolean]
depending on whether the 'Confirm' [true] or 'Cancel' [false] button was tapped.
 * @extends [BottomSheetDialogFragment] that can create the Fragment's contents via Composition.
 */
class ConsentPrompt(
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