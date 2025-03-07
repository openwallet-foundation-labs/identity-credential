package org.multipaz.issuance.funke

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.issuance.CredentialFormat
import org.multipaz.issuance.CredentialRequest

@CborSerializable
data class KeyAttestationCredentialRequest(
    val request: MutableList<CredentialRequest>,
    val format: CredentialFormat,
    val jwtKeyAttestation: String
) {
    companion object
}
