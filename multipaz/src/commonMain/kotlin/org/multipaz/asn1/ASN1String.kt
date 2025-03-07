package org.multipaz.asn1

import kotlinx.io.bytestring.ByteStringBuilder

class ASN1String(
    val value: String,
    tag: Int = ASN1StringTag.UTF8_STRING.tag
): ASN1PrimitiveValue(tag) {

    override fun encode(builder: ByteStringBuilder) {
        val encoded = value.encodeToByteArray()
        ASN1.appendUniversalTagEncodingLength(builder, tag, enc, encoded.size)
        builder.append(encoded)
    }

    override fun equals(other: Any?): Boolean =
        other is ASN1String && other.tag == tag && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String {
        return "ASN1String(${tag}, \"$value\")"
    }

    companion object {
        fun parse(content: ByteArray, tag: Int): ASN1String {
            return ASN1String(content.decodeToString(), tag)
        }
    }
}
