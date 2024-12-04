package com.android.identity.asn1

enum class ASN1TimeTag(val tagNumber: Int) {
    UTC_TIME_TAG(0x17),
    GENERALIZED_TIME_TAG(0x18)
}