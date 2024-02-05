package com.android.identity.crypto

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.dataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseKey
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.coseLabel

/**
 * EC Private Key with Octet Key Pairs.
 *
 * @param x the X coordinate of the public key.
 */
data class EcPrivateKeyOkp(
    override val curve: EcCurve,
    override val d: ByteArray,
    val x: ByteArray
): EcPrivateKey(curve, d) {

    override fun toCoseKey(additionalLabels: Map<CoseLabel, DataItem>): CoseKey {
        return CoseKey(mapOf(
            Pair(Cose.COSE_KEY_KTY.coseLabel, Cose.COSE_KEY_TYPE_OKP.dataItem),
            Pair(Cose.COSE_KEY_PARAM_CRV.coseLabel, curve.coseCurveIdentifier.dataItem),
            Pair(Cose.COSE_KEY_PARAM_D.coseLabel, d.dataItem),
            Pair(Cose.COSE_KEY_PARAM_X.coseLabel, x.dataItem)) + additionalLabels)
    }

    override val publicKey: EcPublicKey
        get() = EcPublicKeyOkp(curve, x)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EcPrivateKeyOkp

        if (curve != other.curve) return false
        if (!d.contentEquals(other.d)) return false
        return x.contentEquals(other.x)
    }

    override fun hashCode(): Int {
        var result = curve.hashCode()
        result = 31 * result + d.contentHashCode()
        result = 31 * result + x.contentHashCode()
        return result
    }

}