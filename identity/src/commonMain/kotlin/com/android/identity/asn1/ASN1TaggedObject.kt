package com.android.identity.asn1

import kotlinx.io.bytestring.ByteStringBuilder

class ASN1TaggedObject(
    tagClass: ASN1TagClass,
    tagNumber: Int,
    val child: ASN1Object
): ASN1Object(
    tagClass = tagClass,
    encoding = ASN1Encoding.CONSTRUCTED,
    tagNumber = tagNumber) {

    override fun encode(builder: ByteStringBuilder) {
        val bsb = ByteStringBuilder()
        child.encode(bsb)
        val childEncoded = bsb.toByteString().toByteArray()
        ASN1.appendIdentifierAndLength(builder, tagClass, encoding, tagNumber, childEncoded.size)
        builder.append(childEncoded)
    }

    override fun equals(other: Any?): Boolean = other is ASN1TaggedObject &&
            tagClass == other.tagClass && tagNumber == other.tagNumber && child == other.child

    override fun hashCode(): Int {
        var result = tagClass.value
        result = 31*result + tagNumber
        result = 31*result + child.hashCode()
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder("ASN1TaggedObject(")
        sb.append("cls=$tagClass, tag=$tagNumber, child=$child")
        sb.append(")")
        return sb.toString()
    }

    companion object {
        fun parse(cls: ASN1TagClass, tagNumber: Int, content: ByteArray): ASN1TaggedObject {
            return ASN1TaggedObject(cls, tagNumber, ASN1.decode(content)!!)
        }
    }
}