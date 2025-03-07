package org.multipaz.cbor

import kotlinx.io.bytestring.ByteStringBuilder

/**
 * Unicode String (major type 3), indefinite length.
 *
 * @param chunks the chunks in the string.
 */
data class IndefLengthTstr(val chunks: List<String>) : DataItem(MajorType.UNICODE_STRING) {

    override fun encode(builder: ByteStringBuilder) {

        val majorTypeShifted = (majorType.type shl 5)
        builder.append((majorTypeShifted + 31).toByte())
        chunks.forEach() {
            val encodedStr = it.encodeToByteArray()
            Cbor.encodeLength(builder, majorType, encodedStr.size)
            builder.append(encodedStr)
        }
        builder.append(0xff.toByte())
    }

    companion object {
        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, IndefLengthTstr> {
            val majorTypeShifted = (MajorType.UNICODE_STRING.type shl 5)
            val marker = (majorTypeShifted + 31).toByte()
            check(encodedCbor[offset] == marker)
            val chunks = mutableListOf<String>()
            var cursor = offset + 1
            while (true) {
                if (encodedCbor[cursor].toInt().and(0xff) == 0xff) {
                    // BREAK code, we're done
                    cursor += 1
                    break
                }
                val (chunkEndOffset, chunk) = Cbor.decode(encodedCbor, cursor)
                check(chunk is Tstr)
                chunks.add(chunk.value)
                check(chunkEndOffset > cursor)
                cursor = chunkEndOffset
            }
            return Pair(cursor, IndefLengthTstr(chunks))
        }
    }

    override fun toString(): String {
        val sb = StringBuilder("IndefLengthTstr(")
        for (chunk in chunks) {
            sb.append("\"$chunk\"")
            sb.append(' ')
        }
        sb.append(")")
        return sb.toString()
    }
}