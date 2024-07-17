package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.EcPublicKey
import com.android.identity.issuance.CredentialFormat
import kotlinx.io.bytestring.ByteString

@CborSerializable
data class FunkeCredentialRequest(
    val authenticationKey: EcPublicKey,
    val format: CredentialFormat,
    val data: ByteString,
)