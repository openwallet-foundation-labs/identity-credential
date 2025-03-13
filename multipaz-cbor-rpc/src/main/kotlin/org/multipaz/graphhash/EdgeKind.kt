package org.multipaz.graphhash

import kotlinx.io.bytestring.ByteString

enum class EdgeKind(
    val mark: ByteString
) {
    REQUIRED(ByteString((0xF0).toByte())),
    OPTIONAL(ByteString((0xF1).toByte())),
    ALTERNATIVE(ByteString((0xF2).toByte())),
}