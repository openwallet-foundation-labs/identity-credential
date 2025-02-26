package com.android.identity.cbor

import com.android.identity.util.appendBstring
import com.android.identity.util.appendUInt8
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.toHexString

/**
 * Byte String (major type 2), indefinite length.
 *
 * @param chunks the chunks in the byte string.
 */
data class IndefLengthBstr(val chunks: List<ByteString>) : DataItem(MajorType.BYTE_STRING) {
    override fun encode(builder: ByteStringBuilder) {
        val majorTypeShifted = (majorType.type shl 5)
        builder.append((majorTypeShifted + 31).toByte())
        chunks.forEach {
            Cbor.encodeLength(builder, majorType, it.size)
            builder.appendBstring(it)
        }
        builder.appendUInt8(0xff)
    }

    companion object {
        internal fun decode(encodedCbor: ByteString, offset: Int): Pair<Int, IndefLengthBstr> {
            val majorTypeShifted = (MajorType.BYTE_STRING.type shl 5)
            val marker = (majorTypeShifted + 31).toByte()
            check(encodedCbor[offset] == marker)
            val chunks = mutableListOf<ByteString>()
            var cursor = offset + 1
            while (true) {
                if (encodedCbor[cursor].toInt().and(0xff) == 0xff) {
                    // BREAK code, we're done
                    cursor += 1
                    break
                }
                val (chunkEndOffset, chunk) = Cbor.decode(encodedCbor, cursor)
                check(chunk is Bstr)
                chunks.add(chunk.value)
                check(chunkEndOffset > cursor)
                cursor = chunkEndOffset
            }
            return Pair(cursor, IndefLengthBstr(chunks))
        }

        private val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString() =
        buildString {
            append("IndefLengthBstr(")
            chunks.forEach {
                append(it.toHexString(HexFormat {
                    upperCase = true
                    number {
                        removeLeadingZeros = false
                    }
                    bytes {
                        bytesPerGroup = 1
                        groupSeparator = " "
                    }
                }))
                append(' ')
            }
            trimEnd(' ')
            append(")")
        }
}