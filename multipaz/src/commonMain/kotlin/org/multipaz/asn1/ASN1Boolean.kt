package org.multipaz.asn1

import kotlinx.io.bytestring.ByteStringBuilder

class ASN1Boolean(val value: Boolean): ASN1PrimitiveValue(tag = TAG_NUMBER) {

    override fun encode(builder: ByteStringBuilder) {
        ASN1.appendUniversalTagEncodingLength(builder, TAG_NUMBER, enc, 1)
        builder.append(if (value) 0xff.toByte() else 0x00.toByte())
    }

    override fun equals(other: Any?): Boolean = other is ASN1Boolean && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String {
        return "ASN1Boolean($value)"
    }

    companion object {
        const val TAG_NUMBER = 0x01

        fun parse(content: ByteArray): ASN1Boolean {
            require(content.size == 1) { "Content size is ${content.size}, expected 1" }
            val value = when (content[0]) {
                0x00.toByte() -> false
                0xff.toByte() -> true
                else -> {
                    throw IllegalArgumentException("Content value is ${content[0]}, expected 0x00 or 0xff")
                }
            }
            return ASN1Boolean(value)
        }
    }
}