package org.multipaz.asn1

enum class ASN1TagClass(val value: Int) {
    UNIVERSAL(0x00),
    APPLICATION(0x40),
    CONTEXT_SPECIFIC(0x80),
    PRIVATE(0xc0)

    ;

    companion object {
        internal fun parse(idOctet0: Byte): ASN1TagClass {
            val upperBits = (idOctet0.toInt().and(0xc0))
            return ASN1TagClass.entries.first() { it.value == upperBits }
        }
    }
}