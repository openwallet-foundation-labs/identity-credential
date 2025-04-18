package org.multipaz.asn1

import org.multipaz.util.toHex
import kotlinx.io.bytestring.ByteStringBuilder

class ASN1OctetString(
    val value: ByteArray
): ASN1PrimitiveValue(tag = TAG_NUMBER) {

    override fun encode(builder: ByteStringBuilder) {
        ASN1.appendUniversalTagEncodingLength(builder, tag, enc, value.size)
        builder.append(value)
    }

    override fun equals(other: Any?): Boolean = other is ASN1OctetString && other.value contentEquals value

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String {
        return "ASN1OctetString(${value.toHex()})"
    }

    companion object {
        const val TAG_NUMBER = 0x04

        fun parse(content: ByteArray): ASN1OctetString {
            return ASN1OctetString(content)
        }
    }
}
