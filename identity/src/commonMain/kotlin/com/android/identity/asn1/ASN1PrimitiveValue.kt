package com.android.identity.asn1

abstract class ASN1PrimitiveValue(
    override val tagNumber: Int,
): ASN1Object(
    tagClass = ASN1TagClass.UNIVERSAL,
    encoding = ASN1Encoding.PRIMITIVE,
    tagNumber = tagNumber) {
}
