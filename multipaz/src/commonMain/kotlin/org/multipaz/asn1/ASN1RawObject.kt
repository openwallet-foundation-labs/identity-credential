package org.multipaz.asn1

import org.multipaz.util.toHex
import kotlinx.io.bytestring.ByteStringBuilder

class ASN1RawObject(
    cls: ASN1TagClass,
    enc: ASN1Encoding,
    tag: Int,
    val content: ByteArray
): ASN1Object(cls, enc, tag) {

    override fun encode(builder: ByteStringBuilder) {
        ASN1.appendUniversalTagEncodingLength(builder, tag, enc, content.size)
        builder.append(content)
    }

    override fun equals(other: Any?): Boolean = other is ASN1RawObject &&
            other.cls == cls &&
            other.enc == enc &&
            other.tag == tag &&
            other.content contentEquals content

    override fun hashCode(): Int = content.contentHashCode()

    override fun toString(): String {
        return "ASN1RawObject($cls, $enc, $tag, ${content.toHex()})"
    }
}