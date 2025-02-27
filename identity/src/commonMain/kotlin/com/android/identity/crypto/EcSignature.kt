package com.android.identity.crypto

import com.android.identity.asn1.ASN1
import com.android.identity.asn1.ASN1Integer
import com.android.identity.asn1.ASN1Sequence
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.append
import kotlinx.io.bytestring.buildByteString

/**
 * An Elliptic Curve Cryptography signature.
 *
 * @param r the R value.
 * @param s the S value.
 */
data class EcSignature(
    val r: ByteString,
    val s: ByteString
) {
    fun toCoseEncoded() = buildByteString { append(r).also { append(s) } }

    fun toDataItem(): DataItem {
        return CborMap.builder().apply {
            put("r", r)
            put("s", s)
        }.end().build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EcSignature

        if (r != other.r) return false
        if (s != other.s) return false

        return true
    }

    override fun hashCode(): Int {
        var result = r.hashCode()
        result = 31 * result + s.hashCode()
        return result
    }

    fun toDerEncoded(): ByteArray {
        // r and s are both encoded without a sign but ASN1Integer uses a sign. So we need
        // to insert zeroes as needed...
        val rS = stripLeadingZeroes(r.toByteArray())
        val sS = stripLeadingZeroes(s.toByteArray())
        val rP = if (rS[0].toInt().and(0x80) != 0) { byteArrayOf(0x00) + rS } else { rS }
        val sP = if (sS[0].toInt().and(0x80) != 0) { byteArrayOf(0x00) + sS } else { sS }
        val derEncoded = ASN1.encode(ASN1Sequence(listOf(
            ASN1Integer(rP),
            ASN1Integer(sP)
        )))
        return derEncoded
    }

    companion object {
        fun fromCoseEncoded(coseSignature: ByteString): EcSignature {
            val len = coseSignature.size
            val r = coseSignature.substring(0, len/2)
            val s = coseSignature.substring(len/2, len)
            return EcSignature(r, s)
        }

        fun fromDataItem(dataItem: DataItem): EcSignature {
            require(dataItem is CborMap)
            return EcSignature(dataItem["r"].asBstr, dataItem["s"].asBstr)
        }

        fun fromDerEncoded(
            keySizeBits: Int,
            derEncodedSignature: ByteString
        ): EcSignature {
            val derSignature = ASN1.decode(derEncodedSignature.toByteArray()) as ASN1Sequence
            val r = (derSignature.elements[0] as ASN1Integer).value
            val s = (derSignature.elements[1] as ASN1Integer).value
            // Need to make sure that each component is exactly as big as the key size.
            val rS = stripLeadingZeroes(r)
            val sS = stripLeadingZeroes(s)
            val keySize = (keySizeBits + 7)/8
            val rPadded = ByteArray(keySize)
            val sPadded = ByteArray(keySize)
            rS.copyInto(rPadded, keySize - rS.size)
            sS.copyInto(sPadded, keySize - sS.size)
            check(rPadded.size == keySize)
            check(sPadded.size == keySize)
            val sig = EcSignature(ByteString(rPadded), ByteString(sPadded))
            return sig
        }
    }
}

private fun stripLeadingZeroes(array: ByteArray): ByteArray {
    val idx = array.indexOfFirst { it != 0.toByte() }
    if (idx == -1)
        return array
    return array.copyOfRange(idx, array.size)
}
