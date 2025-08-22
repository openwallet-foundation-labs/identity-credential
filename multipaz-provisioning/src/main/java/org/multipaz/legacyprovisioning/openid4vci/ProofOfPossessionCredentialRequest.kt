package org.multipaz.legacyprovisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.legacyprovisioning.CredentialFormat
import org.multipaz.legacyprovisioning.CredentialRequest

@CborSerializable
data class ProofOfPossessionCredentialRequest(
    val request: CredentialRequest,
    val format: CredentialFormat,
    val proofOfPossessionJwtHeaderAndBody: String,
    var proofOfPossessionJwtSignature: String? = null
)