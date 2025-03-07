package org.multipaz.issuance.funke

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.issuance.CredentialFormat
import org.multipaz.issuance.CredentialRequest

@CborSerializable
data class ProofOfPossessionCredentialRequest(
    val request: CredentialRequest,
    val format: CredentialFormat,
    val proofOfPossessionJwtHeaderAndBody: String,
    var proofOfPossessionJwtSignature: String? = null
)