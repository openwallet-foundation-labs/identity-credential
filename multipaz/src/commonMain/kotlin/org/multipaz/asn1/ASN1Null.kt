package org.multipaz.asn1

import kotlinx.io.bytestring.ByteStringBuilder

class ASN1Null(): ASN1PrimitiveValue(tag = TAG_NUMBER) {

    override fun encode(builder: ByteStringBuilder) {
        ASN1.appendUniversalTagEncodingLength(builder, TAG_NUMBER, enc, 0)
    }

    override fun equals(other: Any?): Boolean = other is ASN1Null

    override fun hashCode(): Int = 0

    override fun toString(): String {
        return "ASN1Null()"
    }

    companion object {
        const val TAG_NUMBER = 0x05

        fun parse(content: ByteArray): ASN1Null {
            require(content.size == 0) { "Content size is ${content.size}, expected 0" }
            return ASN1Null()
        }
    }
}