package com.android.identity.asn1

import kotlinx.io.bytestring.ByteStringBuilder

class ASN1BitString(
    val numUnusedBits: Int,
    val value: ByteArray
): ASN1PrimitiveValue(tagNumber = TAG_NUMBER) {

    override fun encode(builder: ByteStringBuilder) {
        ASN1.appendUniversalTagEncodingLength(builder, tagNumber, encoding, value.size + 1)
        builder.append(numUnusedBits.toByte())
        builder.append(value)
    }

    override fun equals(other: Any?): Boolean = other is ASN1BitString && other.value contentEquals value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String {
        return "ASN1BitString(${renderBitString()})"
    }

    internal fun renderBitString(): String {
        val sb = StringBuilder()
        for (n in value.indices) {
            val start = if (n == value.size - 1) {
                numUnusedBits
            } else {
                0
            }
            val byteValue = value[n]
            for (m in IntRange(start, 7).reversed()) {
                val bitSet = byteValue.toInt().and(1.shl(m)) != 0
                sb.append(if (bitSet) "1" else "0")
            }
        }
        return sb.toString()
    }

    companion object {
        const val TAG_NUMBER = 0x03

        fun parse(content: ByteArray): ASN1BitString {
            val numUnusedBits = content[0].toInt()
            val encodedBits = content.sliceArray(IntRange(1, content.size - 1))
            return ASN1BitString(numUnusedBits, encodedBits)
        }
    }
}
