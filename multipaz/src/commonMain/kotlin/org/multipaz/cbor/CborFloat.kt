package org.multipaz.cbor

import kotlinx.io.bytestring.ByteStringBuilder
import org.multipaz.util.appendInt32
import org.multipaz.util.getInt32
import kotlin.experimental.or

/**
 * Single-precision floating point number (major type 7).
 *
 * @param value the value.
 */
class CborFloat(val value: Float) : DataItem(MajorType.SPECIAL) {

    override fun encode(builder: ByteStringBuilder) {
            val majorTypeShifted = (majorType.type shl 5).toByte()
            builder.append(majorTypeShifted.or(26))
            builder.appendInt32(value.toRawBits())
    }

    companion object {
        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, CborFloat> {
            return Pair(offset + 5, CborFloat(Float.fromBits(encodedCbor.getInt32(offset + 1))))
        }
    }

    override fun equals(other: Any?): Boolean = other is CborFloat && value.equals(other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "CborFloat($value)"
}
