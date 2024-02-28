package com.android.identity.cbor

import kotlinx.io.bytestring.ByteStringBuilder
import kotlin.experimental.or

/**
 * Single-precision floating point number (major type 7).
 *
 * @param value the value.
 */
class CborDouble(val value: Double) : DataItem(MajorType.SPECIAL) {

    override fun encode(builder: ByteStringBuilder) =
        builder.run {
            val majorTypeShifted = (majorType.type shl 5).toByte()
            append(majorTypeShifted.or(27))

            val raw = value.toRawBits()
            append((raw shr 56).and(0xff).toByte())
            append((raw shr 48).and(0xff).toByte())
            append((raw shr 40).and(0xff).toByte())
            append((raw shr 32).and(0xff).toByte())
            append((raw shr 24).and(0xff).toByte())
            append((raw shr 16).and(0xff).toByte())
            append((raw shr 8).and(0xff).toByte())
            append((raw shr 0).and(0xff).toByte())
        }


    companion object {
        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, CborDouble> {
            val raw = (encodedCbor[offset + 1].toLong().and(0xffL) shl 56) +
                    (encodedCbor[offset + 2].toLong().and(0xffL) shl 48) +
                    (encodedCbor[offset + 3].toLong().and(0xffL) shl 40) +
                    (encodedCbor[offset + 4].toLong().and(0xffL) shl 32) +
                    (encodedCbor[offset + 5].toLong().and(0xffL) shl 24) +
                    (encodedCbor[offset + 6].toLong().and(0xffL) shl 16) +
                    (encodedCbor[offset + 7].toLong().and(0xffL) shl 8) +
                    encodedCbor[offset + 8].toLong().and(0xffL)
            return Pair(offset + 9, CborDouble(Double.fromBits(raw)))
        }
    }

    override fun equals(other: Any?): Boolean = other is CborDouble && value.equals(other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "CborDouble($value)"
}
