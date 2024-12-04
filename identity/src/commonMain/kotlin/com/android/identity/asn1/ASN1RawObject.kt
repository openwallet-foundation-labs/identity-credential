package com.android.identity.asn1

import com.android.identity.util.toHex
import kotlinx.io.bytestring.ByteStringBuilder

class ASN1RawObject(
    tagClass: ASN1TagClass,
    encoding: ASN1Encoding,
    tagNumber: Int,
    val value: ByteArray
): ASN1Object(tagClass, encoding, tagNumber) {

    override fun encode(builder: ByteStringBuilder) {
        ASN1.appendUniversalTagEncodingLength(builder, tagNumber, encoding, value.size)
        builder.append(value)
    }

    override fun equals(other: Any?): Boolean = other is ASN1RawObject &&
            other.tagClass == tagClass &&
            other.encoding == encoding &&
            other.tagNumber == tagNumber &&
            other.value contentEquals value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String {
        return "ASN1RawObject($tagClass, $encoding, $tagNumber, ${value.toHex()})"
    }
}