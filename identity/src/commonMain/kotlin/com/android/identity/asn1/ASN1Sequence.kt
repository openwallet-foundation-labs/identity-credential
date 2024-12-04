package com.android.identity.asn1

import kotlinx.io.bytestring.ByteStringBuilder


class ASN1Sequence(val elements: List<ASN1Object>): ASN1Object(
        tagClass = ASN1TagClass.UNIVERSAL,
        encoding = ASN1Encoding.CONSTRUCTED,
        tagNumber = TAG_NUMBER) {

    override fun encode(builder: ByteStringBuilder) {
        val bsb = ByteStringBuilder()
        for (elem in elements) {
            elem.encode(bsb)
        }
        val encodedElements = bsb.toByteString().toByteArray()
        ASN1.appendUniversalTagEncodingLength(builder, TAG_NUMBER, encoding, encodedElements.size)
        builder.append(encodedElements)
    }

    override fun equals(other: Any?): Boolean = other is ASN1Sequence && elements == other.elements

    override fun hashCode(): Int = elements.hashCode()

    override fun toString(): String {
        val sb = StringBuilder("ASN1Sequence(")
        var first = true
        for (elem in elements) {
            if (!first) {
                sb.append(", ")
            }
            first = false
            sb.append("$elem")
        }
        sb.append(")")
        return sb.toString()
    }

    companion object {
        const val TAG_NUMBER = 0x10

        fun parse(content: ByteArray): ASN1Sequence {
            return ASN1Sequence(ASN1.decodeMultiple(content))
        }
    }
}