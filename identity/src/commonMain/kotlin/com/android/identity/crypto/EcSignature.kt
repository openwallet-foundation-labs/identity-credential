package com.android.identity.crypto

import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem

/**
 * An Elliptic Curve Cryptography signature.
 *
 * @param r the R value.
 * @param s the S value.
 */
data class EcSignature(
    val r: ByteArray,
    val s: ByteArray
) {
    fun toCoseEncoded() = r + s

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

        if (!r.contentEquals(other.r)) return false
        if (!s.contentEquals(other.s)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = r.contentHashCode()
        result = 31 * result + s.contentHashCode()
        return result
    }

    companion object {
        fun fromCoseEncoded(coseSignature: ByteArray): EcSignature {
            val len = coseSignature.size
            val r = coseSignature.sliceArray(IntRange(0, len/2 - 1))
            val s = coseSignature.sliceArray(IntRange(len/2, len - 1))
            return EcSignature(r, s)
        }

        fun fromDataItem(dataItem: DataItem): EcSignature {
            require(dataItem is CborMap)
            return EcSignature(dataItem["r"].asBstr, dataItem["s"].asBstr)
        }
    }
}
