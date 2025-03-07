package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable
import kotlinx.io.bytestring.ByteString

@CborSerializable
data class ClientChallenge(
    val nonce: ByteString,
    val clientId: String,  // if server could not find client id, it will make a new one
)
