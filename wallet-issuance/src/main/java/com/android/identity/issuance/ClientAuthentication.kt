package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.CertificateChain
import kotlinx.io.bytestring.ByteString

@CborSerializable
data class ClientAuthentication(
    val signature: ByteString,
    val certificateChain: CertificateChain?
)
