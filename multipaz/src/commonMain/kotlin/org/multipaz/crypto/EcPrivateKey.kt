package org.multipaz.crypto

import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseKey
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.toCoseLabel


/**
 * An EC private key.
 *
 * @param curve the curve of the key.
 * @param d the private value of the key.
 */
sealed class EcPrivateKey(
    open val curve: EcCurve,
    open val d: ByteArray,
) {

    /**
     * Creates a [CoseKey] object for the key.
     *
     * The resulting object contains [Cose.COSE_KEY_KTY], [Cose.COSE_KEY_PARAM_CRV],
     * [Cose.COSE_KEY_PARAM_D], [Cose.COSE_KEY_PARAM_X] and also [Cose.COSE_KEY_PARAM_Y]
     * in case of a double-coordinate curve.
     *
     * @param additionalLabels additional labels to include.
     */
    abstract fun toCoseKey(additionalLabels: Map<CoseLabel, DataItem> = emptyMap()): CoseKey

    /**
     * Encode this key in PEM format
     *
     * @return a PEM encoded string.
     */
    fun toPem(): String = Crypto.ecPrivateKeyToPem(this)

    fun toDataItem(): DataItem = toCoseKey().toDataItem()

    /**
     * The public part of the key.
     */
    abstract val publicKey: EcPublicKey

    companion object {
        /**
         * Creates an [EcPrivateKey] from a PEM encoded string.
         *
         * @param pemEncoding the PEM encoded string.
         * @param publicKey the corresponding public key.
         * @return a new [EcPrivateKey]
         */
        fun fromPem(pemEncoding: String, publicKey: EcPublicKey): EcPrivateKey =
            Crypto.ecPrivateKeyFromPem(pemEncoding, publicKey)

        /**
         * Gets a [EcPrivateKey] from a COSE Key.
         *
         * @param coseKey the COSE Key.
         * @return the private key.
         */
        fun fromCoseKey(coseKey: CoseKey): EcPrivateKey =
            when (coseKey.keyType) {
                Cose.COSE_KEY_TYPE_EC2.toDataItem() -> {
                    val curve = EcCurve.fromInt(
                        coseKey.labels[Cose.COSE_KEY_PARAM_CRV.toCoseLabel]!!.asNumber.toInt()
                    )
                    val keySizeOctets = (curve.bitSize + 7) / 8
                    val x = coseKey.labels[Cose.COSE_KEY_PARAM_X.toCoseLabel]!!.asBstr
                    val y = coseKey.labels[Cose.COSE_KEY_PARAM_Y.toCoseLabel]!!.asBstr
                    val d = coseKey.labels[Cose.COSE_KEY_PARAM_D.toCoseLabel]!!.asBstr
                    check(x.size == keySizeOctets)
                    check(y.size == keySizeOctets)
                    EcPrivateKeyDoubleCoordinate(curve, d, x, y)
                }

                Cose.COSE_KEY_TYPE_OKP.toDataItem() -> {
                    val curve = EcCurve.fromInt(
                        coseKey.labels[Cose.COSE_KEY_PARAM_CRV.toCoseLabel]!!.asNumber.toInt()
                    )
                    val x = coseKey.labels[Cose.COSE_KEY_PARAM_X.toCoseLabel]!!.asBstr
                    val d = coseKey.labels[Cose.COSE_KEY_PARAM_D.toCoseLabel]!!.asBstr
                    EcPrivateKeyOkp(curve, d, x)
                }

                else -> {
                    throw IllegalArgumentException("Unknown key type ${coseKey.keyType}")
                }
            }

        fun fromDataItem(dataItem: DataItem): EcPrivateKey {
            return fromCoseKey(CoseKey.fromDataItem(dataItem))
        }
    }
}
