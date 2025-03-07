package org.multipaz.asn1

import kotlinx.io.bytestring.ByteStringBuilder

/**
 * Abstract base class for ASN.1 values.
 *
 * @property cls the class of the value.
 * @property enc the encoding of the value, either constructed or primitive.
 * @property tag the tag number.
 */
sealed class ASN1Object(
    open val cls: ASN1TagClass,
    open val enc: ASN1Encoding,
    open val tag: Int
) {

    internal abstract fun encode(builder: ByteStringBuilder)
}
