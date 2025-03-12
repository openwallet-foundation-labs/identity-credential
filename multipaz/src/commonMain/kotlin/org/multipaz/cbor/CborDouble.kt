package org.multipaz.cbor

import kotlinx.io.bytestring.ByteStringBuilder
import org.multipaz.util.appendInt64
import org.multipaz.util.appendUInt64
import org.multipaz.util.getInt64
import kotlin.experimental.or

/**
 * Single-precision floating point number (major type 7).
 *
 * @param value the value.
 */
class CborDouble(val value: Double) : DataItem(MajorType.SPECIAL) {

    override fun encode(builder: ByteStringBuilder) {
            val majorTypeShifted = (majorType.type shl 5).toByte()
            builder.append(majorTypeShifted.or(27))
            builder.appendInt64(value.toRawBits())
    }

    companion object {
        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, CborDouble> {
            return Pair(offset + 9, CborDouble(Double.fromBits(encodedCbor.getInt64(offset + 1))))
        }
    }

    override fun equals(other: Any?): Boolean = other is CborDouble && value.equals(other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "CborDouble($value)"
}
