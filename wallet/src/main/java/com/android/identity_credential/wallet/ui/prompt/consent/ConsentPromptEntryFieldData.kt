package com.android.identity_credential.wallet.ui.prompt.consent

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.identity.document.DocumentRequest
import com.android.identity.document.NameSpacedData
import com.android.identity.issuance.CredentialFormat
import com.android.identity.trustmanagement.TrustPoint

/**
 * Class [ConsentPromptEntryFieldData] is passed to [ConsentPromptEntryField] to show the requested
 * credentials and prompt the user to Confirm sending the requested data or to Cancel.
 */
@Stable
@Immutable
data class ConsentPromptEntryFieldData(
    // Object extracted from the document request
    val documentRequest: DocumentRequest,
    // Requested doc type
    val docType: String?,
    // Document name of credential being used to respond with requested data
    val documentName: String,
    // Data in the credential
    val credentialData: NameSpacedData,
    // Credential format
    val credentialFormat: CredentialFormat,
    // Id of credential that provides documentName - used after Consent Prompt succeeds
    val credentialId: String,
    // Party requesting to verify user's data
    val verifier: TrustPoint? = null,
)

/**
 * [ConsentDataElement] is a wrapper of [DocumentRequest.DataElement] providing the user-facing
 * display name for each element.
 *
 * @param displayName the user-facing name of the credential.
 * @param dataElement the data object of the requested credential
 */
@Stable
@Immutable
data class ConsentDataElement(
    val displayName: String,
    val dataElement: DocumentRequest.DataElement
)