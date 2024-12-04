package com.android.identity.asn1

import com.android.identity.util.toHex
import kotlinx.io.bytestring.ByteStringBuilder

class ASN1RawObject(
    tagClass: ASN1TagClass,
    encoding: ASN1Encoding,
    tagNumber: Int,
    val content: ByteArray
): ASN1Object(tagClass, encoding, tagNumber) {

    override fun encode(builder: ByteStringBuilder) {
        ASN1.appendUniversalTagEncodingLength(builder, tagNumber, encoding, content.size)
        builder.append(content)
    }

    override fun equals(other: Any?): Boolean = other is ASN1RawObject &&
            other.tagClass == tagClass &&
            other.encoding == encoding &&
            other.tagNumber == tagNumber &&
            other.content contentEquals content

    override fun hashCode(): Int = content.hashCode()

    override fun toString(): String {
        return "ASN1RawObject($tagClass, $encoding, $tagNumber, ${content.toHex()})"
    }
}