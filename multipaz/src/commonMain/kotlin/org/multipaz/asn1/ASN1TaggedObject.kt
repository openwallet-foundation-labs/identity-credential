package org.multipaz.asn1

import org.multipaz.util.toHex
import kotlinx.io.bytestring.ByteStringBuilder

class ASN1TaggedObject(
    cls: ASN1TagClass,
    enc: ASN1Encoding,
    tag: Int,
    val content: ByteArray
): ASN1Object(
    cls = cls,
    enc = enc,
    tag = tag) {

    override fun encode(builder: ByteStringBuilder) {
        ASN1.appendIdentifierAndLength(builder, cls, enc, tag, content.size)
        builder.append(content)
    }

    override fun equals(other: Any?): Boolean = other is ASN1TaggedObject &&
            cls == other.cls && tag == other.tag && content contentEquals other.content

    override fun hashCode(): Int {
        var result = cls.value
        result = 31*result + enc.value
        result = 31*result + tag
        result = 31*result + content.contentHashCode()
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder("ASN1TaggedObject(")
        sb.append("cls=${cls}, tag=${tag}, content=${content.toHex()}")
        sb.append(")")
        return sb.toString()
    }

    companion object {
        fun parse(cls: ASN1TagClass, enc: ASN1Encoding, tag: Int, content: ByteArray): ASN1TaggedObject {
            return ASN1TaggedObject(cls, enc, tag, content)
        }
    }
}