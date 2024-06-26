package com.android.identity.crypto

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseKey
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.toCoseLabel

/**
 * EC Public Key with Octet Key Pairs.
 *
 * @param x the X coordinate of the public key.
 */
data class EcPublicKeyOkp(
    override val curve: EcCurve,
    val x: ByteArray
) : EcPublicKey(curve) {

    override fun toCoseKey(additionalLabels: Map<CoseLabel, DataItem>): CoseKey =
        CoseKey(
            mapOf(
                Pair(Cose.COSE_KEY_KTY.toCoseLabel, Cose.COSE_KEY_TYPE_OKP.toDataItem()),
                Pair(Cose.COSE_KEY_PARAM_CRV.toCoseLabel, curve.coseCurveIdentifier.toDataItem()),
                Pair(Cose.COSE_KEY_PARAM_X.toCoseLabel, x.toDataItem())
            ) + additionalLabels
        )

    init {
        when (curve) {
            EcCurve.ED25519,
            EcCurve.X25519,
            EcCurve.X448,
            EcCurve.ED448 -> {
            }

            else -> throw IllegalArgumentException("Unsupported curve $curve")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EcPublicKeyOkp

        if (curve != other.curve) return false
        if (!x.contentEquals(other.x)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = curve.hashCode()
        result = 31 * result + x.contentHashCode()
        return result
    }
}
