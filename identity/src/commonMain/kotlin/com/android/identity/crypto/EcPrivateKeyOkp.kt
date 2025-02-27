package com.android.identity.crypto

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseKey
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.toCoseLabel
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.contentEquals

/**
 * EC Private Key with Octet Key Pairs.
 *
 * @param x the X coordinate of the public key.
 */
data class EcPrivateKeyOkp(
    override val curve: EcCurve,
    override val d: ByteString,
    val x: ByteString
): EcPrivateKey(curve, d) {

    override fun toCoseKey(additionalLabels: Map<CoseLabel, DataItem>): CoseKey {
        return CoseKey(mapOf(
            Pair(Cose.COSE_KEY_KTY.toCoseLabel, Cose.COSE_KEY_TYPE_OKP.toDataItem()),
            Pair(Cose.COSE_KEY_PARAM_CRV.toCoseLabel, curve.coseCurveIdentifier.toDataItem()),
            Pair(Cose.COSE_KEY_PARAM_D.toCoseLabel, d.toDataItem()),
            Pair(Cose.COSE_KEY_PARAM_X.toCoseLabel, x.toDataItem())) + additionalLabels)
    }

    override val publicKey: EcPublicKey
        get() = EcPublicKeyOkp(curve, x)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EcPrivateKeyOkp

        if (curve != other.curve) return false
        if (d != other.d) return false
        if (x != other.x) return false

        return true
    }

    override fun hashCode(): Int {
        var result = curve.hashCode()
        result = 31 * result + d.hashCode()
        result = 31 * result + x.hashCode()
        return result
    }
}