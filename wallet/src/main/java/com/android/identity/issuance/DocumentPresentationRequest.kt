package com.android.identity.issuance

import com.android.identity.crypto.CertificateChain

/**
 * This data structure contains the request for a Document Presentation Object.
 */
data class DocumentPresentationRequest(
    /**
     * The requested credential presentation format.
     */
    val documentPresentationFormat: DocumentPresentationFormat,

    /**
     * The attestation for the authentication key that was created and to be referenced
     * in the requested Document Presentation Object.
     */
    val authenticationKeyAttestation: CertificateChain,

    // TODO: include proof that each key exist in the same Secure Hardware as DocumentKey
)