package org.multipaz.asn1

import kotlinx.io.bytestring.ByteStringBuilder
import org.multipaz.util.appendByteArray
import org.multipaz.util.appendUInt8

class ASN1BitString(
    val numUnusedBits: Int,
    val value: ByteArray
): ASN1PrimitiveValue(tag = TAG_NUMBER) {

    constructor(booleanValues: BooleanArray):
            this(
                if (booleanValues.size == 0) 0 else (8 - (booleanValues.size % 8)),
                encodeBooleans(booleanValues)
            )

    override fun encode(builder: ByteStringBuilder) {
        ASN1.appendUniversalTagEncodingLength(builder, tag, enc, value.size + 1)
        builder.appendUInt8(numUnusedBits)
        builder.appendByteArray(value)
    }

    override fun equals(other: Any?): Boolean = other is ASN1BitString && other.value contentEquals value

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String {
        return "ASN1BitString(${renderBitString()})"
    }

    fun asBooleans(): BooleanArray {
        val result = mutableListOf<Boolean>()
        for (n in IntRange(0, value.size*8 - numUnusedBits - 1)) {
            val offset = n/8
            val bitNum = 7 - (n - offset*8)
            val boolVal = if (value[offset].toInt().and(1.shl(bitNum)) != 0x00) {
                true
            } else {
                false
            }
            result.add(boolVal)
        }
        return result.toBooleanArray()
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

        private fun encodeBooleans(booleanValues: BooleanArray): ByteArray {
            if (booleanValues.size == 0) {
                return byteArrayOf()
            }
            val encodedBooleans = mutableListOf<Byte>()
            val numBytes = (booleanValues.size + 7)/8
            for (n in IntRange(0, numBytes - 1)) {
                var b: Int = 0
                for (m in IntRange(0, 7)) {
                    val booleanNum = n*8 + m
                    if (booleanNum < booleanValues.size) {
                        if (booleanValues[booleanNum]) {
                            b = b or (1.shl(7 - m))
                        }
                    }
                }
                encodedBooleans.add(b.and(0xff).toByte())
            }
            return encodedBooleans.toByteArray()
        }

        fun parse(content: ByteArray): ASN1BitString {
            val numUnusedBits = content[0].toInt()
            val encodedBits = content.sliceArray(IntRange(1, content.size - 1))
            return ASN1BitString(numUnusedBits, encodedBits)
        }
    }
}
