package org.multipaz.asn1

enum class ASN1TimeTag(val tag: Int) {
    UTC_TIME(0x17),
    GENERALIZED_TIME(0x18)
}