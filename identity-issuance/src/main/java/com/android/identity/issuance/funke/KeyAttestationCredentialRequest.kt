package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.CredentialRequest

@CborSerializable
data class KeyAttestationCredentialRequest(
    val request: MutableList<CredentialRequest>,
    val format: CredentialFormat,
    val jwtKeyAttestation: String
) {
    companion object
}
