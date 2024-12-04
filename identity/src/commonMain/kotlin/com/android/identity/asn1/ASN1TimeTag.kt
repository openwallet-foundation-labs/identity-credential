package com.android.identity.asn1

enum class ASN1TimeTag(val tagNumber: Int) {
    UTC_TIME(0x17),
    GENERALIZED_TIME(0x18)
}