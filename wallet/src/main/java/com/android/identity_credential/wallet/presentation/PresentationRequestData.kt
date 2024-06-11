package com.android.identity_credential.wallet.presentation

import com.android.identity.document.Document
import com.android.identity.document.DocumentRequest
import com.android.identity.trustmanagement.TrustPoint

/**
 * Data class containing various data types needed to start an MDL Presentation which is parsed
 * from the request bytes. During Presentment, the user is faced with one or more prompts
 * (consent, biometric, etc..) to authenticate for unlocking the authentication key. Once all the
 * prompts have been successful, this [PresentationRequestData] object is used to finish processing
 * the request and ultimately produce the response data bytes to send to the Verifier.
 */
data class PresentationRequestData (
    val document: Document,
    val documentRequest: DocumentRequest,
    val docType : String,
    val trustPoint : TrustPoint?,
    val sessionTranscript: ByteArray
)