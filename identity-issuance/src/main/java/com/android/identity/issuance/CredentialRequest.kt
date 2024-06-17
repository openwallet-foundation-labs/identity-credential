package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.securearea.KeyAttestation

/**
 * A request for the issuer to mint a credential
 */
@CborSerializable
data class CredentialRequest(
    /**
     * The attestation for the secure-area bound credential that was created and to be referenced
     * in the minted credential.
     */
    val secureAreaBoundKeyAttestation: KeyAttestation,

    // TODO: include proof that each key exist in the same device as where evidence was collected
)