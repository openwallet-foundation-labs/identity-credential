package org.multipaz.asn1

abstract class ASN1PrimitiveValue(
    override val tag: Int,
): ASN1Object(
    cls = ASN1TagClass.UNIVERSAL,
    enc = ASN1Encoding.PRIMITIVE,
    tag = tag) {
}
