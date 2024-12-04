package com.android.identity.asn1

import kotlinx.io.bytestring.ByteStringBuilder

sealed class ASN1Object(
    open val tagClass: ASN1TagClass,
    open val encoding: ASN1Encoding,
    open val tagNumber: Int
) {

    internal abstract fun encode(builder: ByteStringBuilder)
}
