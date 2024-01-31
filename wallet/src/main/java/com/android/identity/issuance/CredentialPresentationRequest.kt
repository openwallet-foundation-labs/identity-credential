package com.android.identity.issuance

import java.security.PublicKey
import java.security.cert.X509Certificate

/**
 * This data structure contains the request for a Credential Presentation Object.
 */
data class CredentialPresentationRequest(
    /**
     * The requested credential presentation format.
     */
    val credentialPresentationFormat: CredentialPresentationFormat,

    /**
     * The attestation for the authentication key that was created and to be referenced
     * in the requested Credential Presentation Object.
     */
    val authenticationKeyAttestation: List<X509Certificate>,

    // TODO: include proof that each key exist in the same Secure Hardware as CredentialKey
)