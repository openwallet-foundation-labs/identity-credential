package com.android.identity.crypto

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseKey
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.toCoseLabel
import kotlinx.io.bytestring.ByteString

/**
 * EC Private Key with two coordinates.
 *
 * @param x the X coordinate of the public key.
 * @param y the Y coordinate of the public key.
 */
data class EcPrivateKeyDoubleCoordinate(
    override val curve: EcCurve,
    override val d: ByteString,
    val x: ByteString,
    val y: ByteString
) : EcPrivateKey(curve, d) {

    override fun toCoseKey(additionalLabels: Map<CoseLabel, DataItem>): CoseKey {
        return CoseKey(
            mapOf(
                Pair(Cose.COSE_KEY_KTY.toCoseLabel, Cose.COSE_KEY_TYPE_EC2.toDataItem()),
                Pair(Cose.COSE_KEY_PARAM_CRV.toCoseLabel, curve.coseCurveIdentifier.toDataItem()),
                Pair(Cose.COSE_KEY_PARAM_D.toCoseLabel, d.toDataItem()),
                Pair(Cose.COSE_KEY_PARAM_X.toCoseLabel, x.toDataItem()),
                Pair(Cose.COSE_KEY_PARAM_Y.toCoseLabel, y.toDataItem())
            ) + additionalLabels
        )
    }

    override val publicKey: EcPublicKey
        get() = EcPublicKeyDoubleCoordinate(curve, x, y)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EcPrivateKeyDoubleCoordinate

        if (curve != other.curve) return false
        if (d != other.d) return false
        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    override fun hashCode(): Int {
        var result = curve.hashCode()
        result = 31 * result + d.hashCode()
        result = 31 * result + x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }
}