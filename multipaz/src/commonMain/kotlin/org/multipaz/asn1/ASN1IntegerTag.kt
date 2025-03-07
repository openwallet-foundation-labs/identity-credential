package org.multipaz.asn1

enum class ASN1IntegerTag(val tag: Int) {
    INTEGER(0x02),
    ENUMERATED(0x0a),
}