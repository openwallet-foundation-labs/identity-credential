package com.android.identity.crypto

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseKey
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.toCoseLabel
import kotlinx.io.bytestring.ByteStringBuilder

/**
 * EC Public Key with two coordinates.
 *
 * @param x the X coordinate of the public key.
 * @param y the Y coordinate of the public key.
 */
data class EcPublicKeyDoubleCoordinate(
    override val curve: EcCurve,
    val x: ByteArray,
    val y: ByteArray
) : EcPublicKey(curve) {

    override fun toCoseKey(additionalLabels: Map<CoseLabel, DataItem>): CoseKey =
        CoseKey(
            mapOf(
                Pair(Cose.COSE_KEY_KTY.toCoseLabel, Cose.COSE_KEY_TYPE_EC2.toDataItem()),
                Pair(Cose.COSE_KEY_PARAM_CRV.toCoseLabel, curve.coseCurveIdentifier.toDataItem()),
                Pair(Cose.COSE_KEY_PARAM_X.toCoseLabel, x.toDataItem()),
                Pair(Cose.COSE_KEY_PARAM_Y.toCoseLabel, y.toDataItem())
            ) + additionalLabels
        )

    init {
        when (curve) {
            EcCurve.ED25519,
            EcCurve.X25519,
            EcCurve.X448,
            EcCurve.ED448 -> throw IllegalArgumentException("Unsupported curve $curve")
            else -> {}
        }
        check(x.size == (curve.bitSize + 7)/8)
        check(y.size == (curve.bitSize + 7)/8)
    }

    /**
     * The uncompressed point encoding of the key.
     *
     * This is according to SEC 1: Elliptic Curve Cryptography, section 2.3.3
     * Elliptic-Curve-Point-to-Octet-String Conversion.
     *
     * This is the reverse operation of [fromUncompressedPointEncoding].
     */
    val asUncompressedPointEncoding: ByteArray
        get() {
            val builder = ByteStringBuilder()
            builder.append(0x04)
            builder.append(x)
            builder.append(y)
            return builder.toByteString().toByteArray()
        }

    companion object {
        /**
         * Creates a key from uncompressed point encoding.
         *
         * This is according to SEC 1: Elliptic Curve Cryptography, section 2.3.3
         * Elliptic-Curve-Point-to-Octet-String Conversion.
         *
         * This is the reverse of [asUncompressedPointEncoding].
         *
         * @param curve the curve.
         * @param encoded the encoded bytes.
         */
        fun fromUncompressedPointEncoding(
            curve: EcCurve,
            encoded: ByteArray): EcPublicKeyDoubleCoordinate {
            val coordinateSize = (curve.bitSize + 7)/8
            check(encoded.size == 1 + 2*coordinateSize)
            require(encoded[0].toInt() == 0x04)
            return EcPublicKeyDoubleCoordinate(
                curve,
                encoded.sliceArray(IntRange(1, 1 + coordinateSize - 1)),
                encoded.sliceArray(IntRange(1 + coordinateSize, encoded.size - 1))
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EcPublicKeyDoubleCoordinate

        if (curve != other.curve) return false
        if (!x.contentEquals(other.x)) return false
        if (!y.contentEquals(other.y)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = curve.hashCode()
        result = 31 * result + x.contentHashCode()
        result = 31 * result + y.contentHashCode()
        return result
    }
}
