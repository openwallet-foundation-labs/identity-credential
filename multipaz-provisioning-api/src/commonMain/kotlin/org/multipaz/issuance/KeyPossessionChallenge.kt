package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable
import kotlinx.io.bytestring.ByteString

@CborSerializable
data class KeyPossessionChallenge(
    val messageToSign: ByteString
)
