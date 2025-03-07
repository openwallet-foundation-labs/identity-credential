package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.securearea.KeyAttestation

/**
 * A request for the issuer to mint a credential
 */
@CborSerializable
data class CredentialRequest(
    /**
     * The certificate chain in the attestation for the secure-area bound credential that
     * was created and to be referenced in the minted credential.
     */
    val secureAreaBoundKeyAttestation: KeyAttestation,
)