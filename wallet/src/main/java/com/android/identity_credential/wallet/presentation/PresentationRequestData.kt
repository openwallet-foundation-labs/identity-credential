package com.android.identity_credential.wallet.presentation

import com.android.identity.credential.Credential
import com.android.identity.credential.CredentialRequest

/**
 * Contains data produced after starting to process a Presentation request.
 * The user is then faced with one or more prompts (consent, biometric, etc..) and upon accepting
 * them, this PresentationRequestData object is used to finish processing the request and ultimately
 * produce the response data to send to the party who initiated the Presentation.
 */
data class PresentationRequestData (
    val credential: Credential,
    val credentialRequest: CredentialRequest,
    val docType : String
)