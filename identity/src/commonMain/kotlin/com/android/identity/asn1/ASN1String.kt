package com.android.identity.asn1

import kotlinx.io.bytestring.ByteStringBuilder

class ASN1String(
    val value: String,
    tagNumber: Int = ASN1StringTag.UTF8_STRING.tagNumber
): ASN1PrimitiveValue(tagNumber) {

    override fun encode(builder: ByteStringBuilder) {
        val encoded = value.encodeToByteArray()
        ASN1.appendUniversalTagEncodingLength(builder, tagNumber, encoding, encoded.size)
        // TODO: validate/encode depending on [tagNumber]
        builder.append(encoded)
    }

    override fun equals(other: Any?): Boolean =
        other is ASN1String && other.tagNumber == tagNumber && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String {
        return "ASN1String($tagNumber, \"$value\")"
    }

    companion object {
        fun parse(content: ByteArray, tagNumber: Int): ASN1String {
            // TODO: validate/decode depending on [tagNumber]
            return ASN1String(content.decodeToString(), tagNumber)
        }
    }
}
