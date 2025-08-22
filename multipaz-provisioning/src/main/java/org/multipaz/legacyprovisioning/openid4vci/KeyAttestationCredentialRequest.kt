package org.multipaz.legacyprovisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.legacyprovisioning.CredentialFormat
import org.multipaz.legacyprovisioning.CredentialRequest

@CborSerializable
data class KeyAttestationCredentialRequest(
    val request: MutableList<CredentialRequest>,
    val format: CredentialFormat,
    val jwtKeyAttestation: String
) {
    companion object
}
