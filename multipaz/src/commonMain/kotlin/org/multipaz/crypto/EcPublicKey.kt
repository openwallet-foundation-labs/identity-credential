package org.multipaz.crypto

import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseKey
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.toCoseLabel
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * An EC Public Key.
 *
 * @param curve the curve of the key.
 */
sealed class EcPublicKey(
    open val curve: EcCurve
) {

    /**
     * Creates a [CoseKey] object for the key.
     *
     * The resulting object contains [Cose.COSE_KEY_KTY], [Cose.COSE_KEY_PARAM_CRV],
     * [Cose.COSE_KEY_PARAM_X] and also [Cose.COSE_KEY_PARAM_Y] in case of a double-
     * coordinate curve.
     *
     * @param additionalLabels additional labels to include.
     */
    abstract fun toCoseKey(additionalLabels: Map<CoseLabel, DataItem> = emptyMap()): CoseKey

    /**
     * Encode this key in PEM format
     *
     * @return a PEM encoded string.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun toPem(): String = Crypto.ecPublicKeyToPem(this)

    fun toDataItem(): DataItem = toCoseKey().toDataItem()

    companion object {
        /**
         * Creates an [EcPublicKey] from a PEM encoded string.
         *
         * @param pemEncoding the PEM encoded string.
         * @param curve the curve of the key..
         * @return a new [EcPublicKey].
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun fromPem(pemEncoding: String, curve: EcCurve): EcPublicKey =
            Crypto.ecPublicKeyFromPem(pemEncoding, curve)

        /**
         * Gets a [EcPublicKey] from a COSE Key.
         *
         * @param coseKey the COSE Key.
         * @return the public key.
         */
        fun fromCoseKey(coseKey: CoseKey): EcPublicKey =
            when (coseKey.keyType) {
                Cose.COSE_KEY_TYPE_EC2.toDataItem() -> {
                    val curve = EcCurve.fromInt(
                        coseKey.labels[Cose.COSE_KEY_PARAM_CRV.toCoseLabel]!!.asNumber.toInt()
                    )
                    val keySizeOctets = (curve.bitSize + 7) / 8
                    val x = coseKey.labels[Cose.COSE_KEY_PARAM_X.toCoseLabel]!!.asBstr
                    val y = coseKey.labels[Cose.COSE_KEY_PARAM_Y.toCoseLabel]!!.asBstr
                    check(x.size == keySizeOctets)
                    check(y.size == keySizeOctets)
                    // TODO: maybe check that (x, y) is a point on the curve?
                    EcPublicKeyDoubleCoordinate(curve, x, y)
                }

                Cose.COSE_KEY_TYPE_OKP.toDataItem() -> {
                    val curve = EcCurve.fromInt(
                        coseKey.labels[Cose.COSE_KEY_PARAM_CRV.toCoseLabel]!!.asNumber.toInt()
                    )
                    val x = coseKey.labels[Cose.COSE_KEY_PARAM_X.toCoseLabel]!!.asBstr
                    EcPublicKeyOkp(curve, x)
                }

                else -> {
                    throw IllegalArgumentException("Unknown key type $coseKey.keyType")
                }
            }

        fun fromDataItem(dataItem: DataItem): EcPublicKey {
            return CoseKey.fromDataItem(dataItem).ecPublicKey
        }
    }
}
