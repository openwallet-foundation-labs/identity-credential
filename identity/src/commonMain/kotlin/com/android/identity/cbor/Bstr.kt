package com.android.identity.cbor

import com.android.identity.util.appendBstring
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.toHexString

/**
 * Byte String (major type 2).
 *
 * @param value the [ByteString] for the value of the byte string.
 */
data class Bstr(val value: ByteString) : DataItem(MajorType.BYTE_STRING) {
    override fun encode(builder: ByteStringBuilder) {
        Cbor.encodeLength(builder, majorType, value.size)
        builder.appendBstring(value)
    }

    companion object {
        internal fun decode(encodedCbor: ByteString, offset: Int): Pair<Int, Bstr> {
            val (payloadBegin, length) = Cbor.decodeLength(encodedCbor, offset)
            val payloadEnd = payloadBegin + length.toInt()
            val slice = encodedCbor.substring(payloadBegin, payloadEnd)
            return Pair(payloadEnd, Bstr(slice))
        }
    }

    override fun equals(other: Any?): Boolean = other is Bstr && value == other.value

    override fun hashCode(): Int = value.hashCode()

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString() =
        buildString {
            append("Bstr(")
            append(value.toHexString(HexFormat {
                upperCase = true
                number {
                    removeLeadingZeros = false
                }
                bytes {
                    bytesPerGroup = 1
                    groupSeparator = " "
                }
            }))
            append(")")
        }
}