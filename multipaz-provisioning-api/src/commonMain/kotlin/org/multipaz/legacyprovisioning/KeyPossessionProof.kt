package org.multipaz.legacyprovisioning

import org.multipaz.cbor.annotation.CborSerializable
import kotlinx.io.bytestring.ByteString

@CborSerializable
data class KeyPossessionProof(
    val signature: ByteString
)
