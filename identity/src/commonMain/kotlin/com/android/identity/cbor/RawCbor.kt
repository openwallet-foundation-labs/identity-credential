package com.android.identity.cbor

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import kotlinx.io.bytestring.toHexString

/**
 * Data item for raw CBOR.
 *
 * This type can be used to insert a raw CBOR into arrays, maps, or tags without
 * having to first use [Cbor.decode] to obtain a [DataItem]. This is never
 * returned when decoding bytes of valid CBOR.
 *
 * Note that the validity of the passed in encoded CBOR value isn't verified so
 * care should be taken when using this.
 *
 * @param encodedCbor the bytes of valid CBOR.
 */
class RawCbor(val encodedCbor: ByteString)
    : DataItem(MajorType.fromInt(encodedCbor[0].toInt().and(0xff) ushr 5)) {

    override fun encode(builder: ByteStringBuilder) {
        builder.append(encodedCbor)
    }

    override fun equals(other: Any?): Boolean = other is RawCbor && encodedCbor == other.encodedCbor

    override fun hashCode(): Int = encodedCbor.hashCode()

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return buildString {
            append("RawCbor(")
            append(encodedCbor.toHexString())
            append(")")
        }
    }
}