package com.android.identity.cbor

import kotlinx.io.bytestring.ByteStringBuilder
import kotlin.experimental.or

/**
 * Single-precision floating point number (major type 7).
 *
 * @param value the value.
 */
class CborFloat(val value: Float) : DataItem(MajorType.SPECIAL) {

    override fun encode(builder: ByteStringBuilder) {
        builder.run {
            val majorTypeShifted = (majorType.type shl 5).toByte()
            append(majorTypeShifted.or(26))

            val raw = value.toRawBits()
            append((raw shr 24).and(0xff).toByte())
            append((raw shr 16).and(0xff).toByte())
            append((raw shr  8).and(0xff).toByte())
            append((raw shr  0).and(0xff).toByte())
        }

    }

    companion object {
        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, CborFloat> {
            val raw = (encodedCbor[offset + 1].toInt().and(0xff) shl 24) +
                    (encodedCbor[offset + 2].toInt().and(0xff) shl 16) +
                    (encodedCbor[offset + 3].toInt().and(0xff) shl 8) +
                    encodedCbor[offset + 4].toInt().and(0xff)
            return Pair(offset + 5, CborFloat(Float.fromBits(raw)))
        }
    }

    override fun equals(other: Any?): Boolean = other is CborFloat && value.equals(other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "CborFloat($value)"
}
