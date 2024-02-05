package com.android.identity.crypto

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.dataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseKey
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.coseLabel

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

    override fun toCoseKey(additionalLabels: Map<CoseLabel, DataItem>): CoseKey {
        return CoseKey(mapOf(
            Pair(Cose.COSE_KEY_KTY.coseLabel, Cose.COSE_KEY_TYPE_EC2.dataItem),
            Pair(Cose.COSE_KEY_PARAM_CRV.coseLabel, curve.coseCurveIdentifier.dataItem),
            Pair(Cose.COSE_KEY_PARAM_X.coseLabel, x.dataItem),
            Pair(Cose.COSE_KEY_PARAM_Y.coseLabel, y.dataItem)) + additionalLabels)
    }

    init {
        when (curve) {
            EcCurve.ED25519,
            EcCurve.X25519,
            EcCurve.X448,
            EcCurve.ED448 -> throw IllegalArgumentException("Unsupported curve $curve")
            else -> {}
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EcPublicKeyDoubleCoordinate

        if (curve != other.curve) return false
        if (!x.contentEquals(other.x)) return false
        return y.contentEquals(other.y)
    }

    override fun hashCode(): Int {
        var result = curve.hashCode()
        result = 31 * result + x.contentHashCode()
        result = 31 * result + y.contentHashCode()
        return result
    }
}
