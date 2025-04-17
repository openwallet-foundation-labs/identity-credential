package com.android.identity.testapp.provisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.provisioning.CredentialFormat
import org.multipaz.provisioning.CredentialRequest

@CborSerializable
data class ProofOfPossessionCredentialRequest(
    val request: CredentialRequest,
    val format: CredentialFormat,
    val proofOfPossessionJwtHeaderAndBody: String,
    var proofOfPossessionJwtSignature: String? = null
)