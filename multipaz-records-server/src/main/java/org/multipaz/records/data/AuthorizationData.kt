package org.multipaz.records.data

import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable

@CborSerializable
data class AuthorizationData(
    val scopeAndId: String,
    val codeChallenge: ByteString,
    val expiration: Instant
) {
    companion object
}