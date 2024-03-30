package com.android.identity.issuance

import com.android.identity.crypto.CertificateChain

/**
 * A request for the issuer to mint a credential
 */
data class CredentialRequest(
    /**
     * The requested credential presentation format.
     */
    val format: CredentialFormat,

    /**
     * The attestation for the secure-area bound credential that was created and to be referenced
     * in the minted credential.
     */
    val secureAreaBoundKeyAttestation: CertificateChain,

    // TODO: include proof that each key exist in the same device as where evidence was collected
)