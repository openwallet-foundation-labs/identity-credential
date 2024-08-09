package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import kotlinx.io.bytestring.ByteString

@CborSerializable
data class KeyPossessionChallenge(
    val messageToSign: ByteString
)
