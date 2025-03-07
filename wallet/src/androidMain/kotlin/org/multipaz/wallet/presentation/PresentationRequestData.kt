package org.multipaz_credential.wallet.presentation

import org.multipaz.document.Document
import org.multipaz.document.DocumentRequest
import org.multipaz.trustmanagement.TrustPoint

/**
 * Contains data produced after starting to process a Presentation request.
 * The user is then faced with one or more prompts (consent, biometric, etc..) and upon accepting
 * them, this PresentationRequestData object is used to finish processing the request and ultimately
 * produce the response data to send to the party who initiated the Presentation.
 */
data class PresentationRequestData (
    val document: Document,
    val documentRequest: DocumentRequest,
    val docType : String,
    val trustPoint : TrustPoint?
)