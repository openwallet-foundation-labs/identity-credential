package org.multipaz.provisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.provisioning.CredentialFormat
import org.multipaz.provisioning.CredentialRequest

@CborSerializable
data class KeyAttestationCredentialRequest(
    val request: MutableList<CredentialRequest>,
    val format: CredentialFormat,
    val jwtKeyAttestation: String
) {
    companion object
}
