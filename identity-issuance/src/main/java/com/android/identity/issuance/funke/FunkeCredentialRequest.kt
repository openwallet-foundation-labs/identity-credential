package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.EcPublicKey
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.CredentialRequest
import kotlinx.io.bytestring.ByteString

@CborSerializable
data class FunkeCredentialRequest(
    val request: CredentialRequest,
    val format: CredentialFormat,
    val proofOfPossessionJwtHeaderAndBody: String,
    var proofOfPossessionJwtSignature: String? = null
)