package org.multipaz.asn1

enum class ASN1Encoding(val value: Int) {
    PRIMITIVE(0x00),
    CONSTRUCTED(0x20)

    ;

    companion object {
        internal fun parse(idOctet0: Byte): ASN1Encoding {
            val bits = (idOctet0.toInt().and(0x20))
            return ASN1Encoding.entries.first() { it.value == bits }
        }
    }
}