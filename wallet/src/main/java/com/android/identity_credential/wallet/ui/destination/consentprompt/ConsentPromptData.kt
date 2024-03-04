package com.android.identity_credential.wallet.ui.destination.consentprompt

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.identity.credential.CredentialRequest
import com.android.identity.trustmanagement.TrustPoint

/**
 * ConsentPromptData is passed to ConsentPrompt to show the appropriate requested credentials.
 */
@Stable
@Immutable
data class ConsentPromptData(
    // Object extracted from the document request
    val credentialRequest: CredentialRequest,
    // requested doc type
    val docType: String,
    // document name of credential being used to respond with requested data
    val documentName: String,
    // id of credential that provides documentName - used after Consent Prompt succeeds
    val credentialId: String,
    // party requesting to verify user's data
    val verifier: TrustPoint? = null,
)

/**
 * ConsentDataElement is a wrapper of CredentialRequest.DataElement providing the user-facing display name
 * for each element.
 */
@Stable
@Immutable
data class ConsentDataElement(
    val displayName: String,
    val dataElement: CredentialRequest.DataElement
)