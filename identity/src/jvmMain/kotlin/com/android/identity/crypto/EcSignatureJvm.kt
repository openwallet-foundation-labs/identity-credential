package com.android.identity.crypto

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequenceGenerator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger

fun EcSignature.toDer(): ByteArray {
    // r and s are always positive and may use all bits so use the constructor which
    // parses them as unsigned.
    val rBigInt = BigInteger(1, r)
    val sBigInt = BigInteger(1, s)
    val baos = ByteArrayOutputStream()
    try {
        DERSequenceGenerator(baos).apply {
            addObject(ASN1Integer(rBigInt.toByteArray()))
            addObject(ASN1Integer(sBigInt.toByteArray()))
            close()
        }
    } catch (e: IOException) {
        throw IllegalStateException("Error generating DER signature", e)
    }
    return baos.toByteArray()
}

fun EcSignature.Companion.fromDer(curve: EcCurve, derEncodedSignature: ByteArray): EcSignature {
    val asn1 = try {
        ASN1InputStream(ByteArrayInputStream(derEncodedSignature)).readObject()
    } catch (e: IOException) {
        throw IllegalArgumentException("Error decoding DER signature", e)
    }
    val asn1Encodables = (asn1 as ASN1Sequence).toArray()
    require(asn1Encodables.size == 2) { "Expected two items in sequence" }
    val r = stripLeadingZeroes(((asn1Encodables[0].toASN1Primitive() as ASN1Integer).value).toByteArray())
    val s = stripLeadingZeroes(((asn1Encodables[1].toASN1Primitive() as ASN1Integer).value).toByteArray())

    val keySize = (curve.bitSize + 7)/8
    check(r.size <= keySize)
    check(s.size <= keySize)

    val rPadded = ByteArray(keySize)
    val sPadded = ByteArray(keySize)
    r.copyInto(rPadded, keySize - r.size)
    s.copyInto(sPadded, keySize - s.size)

    check(rPadded.size == keySize)
    check(sPadded.size == keySize)

    return EcSignature(rPadded, sPadded)
}

private fun stripLeadingZeroes(array: ByteArray): ByteArray {
    val idx = array.indexOfFirst { it != 0.toByte() }
    if (idx == -1)
        return array
    return array.copyOfRange(idx, array.size)
}

