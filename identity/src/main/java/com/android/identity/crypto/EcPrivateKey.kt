package com.android.identity.crypto

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseKey
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.toCoseLabel
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.util.BigIntegers
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec


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
     * The public part of the key.
     */
    abstract val publicKey: EcPublicKey

    companion object {
        /**
         * Gets a [EcPrivateKey] from a COSE Key.
         *
         * @param coseKey the COSE Key.
         * @return the private key.
         */
        fun fromCoseKey(coseKey: CoseKey): EcPrivateKey =
            when (coseKey.keyType) {
                Cose.COSE_KEY_TYPE_EC2.toDataItem -> {
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

                Cose.COSE_KEY_TYPE_OKP.toDataItem -> {
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
    }
}

// TODO: move to identity-jvm library

fun PrivateKey.toEcPrivateKey(publicKey: PublicKey, curve: EcCurve): EcPrivateKey =
    when (curve) {
        EcCurve.P256,
        EcCurve.P384,
        EcCurve.P521,
        EcCurve.BRAINPOOLP256R1,
        EcCurve.BRAINPOOLP320R1,
        EcCurve.BRAINPOOLP384R1,
        EcCurve.BRAINPOOLP512R1 -> {
            val pub = publicKey.toEcPublicKey(curve) as EcPublicKeyDoubleCoordinate
            val priv = this as ECPrivateKey
            EcPrivateKeyDoubleCoordinate(curve, priv.d.toByteArray(), pub.x, pub.y)
        }

        EcCurve.ED25519,
        EcCurve.X25519,
        EcCurve.ED448,
        EcCurve.X448 -> {
            val pub = publicKey.toEcPublicKey(curve) as EcPublicKeyOkp
            val privateKeyInfo = PrivateKeyInfo.getInstance(this.getEncoded())
            val encoded = privateKeyInfo.parsePrivateKey().toASN1Primitive().encoded
            EcPrivateKeyOkp(curve, encoded.sliceArray(IntRange(2, encoded.size - 1)), pub.x)
        }
    }

val EcPrivateKey.javaPrivateKey: PrivateKey
    get() = when (this.curve) {
        EcCurve.P256,
        EcCurve.P384,
        EcCurve.P521,
        EcCurve.BRAINPOOLP256R1,
        EcCurve.BRAINPOOLP320R1,
        EcCurve.BRAINPOOLP384R1,
        EcCurve.BRAINPOOLP512R1 -> {
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePrivate(
                ECPrivateKeySpec(
                    BigIntegers.fromUnsignedByteArray(this.d),
                    ECNamedCurveTable.getParameterSpec(this.curve.SECGName)
                )
            )
        }

        EcCurve.ED25519,
        EcCurve.X25519,
        EcCurve.ED448,
        EcCurve.X448 -> {
            val ids = when (this.curve) {
                EcCurve.ED25519 -> Pair("Ed25519", EdECObjectIdentifiers.id_Ed25519)
                EcCurve.X25519 -> Pair("X25519", EdECObjectIdentifiers.id_X25519)
                EcCurve.ED448 -> Pair("Ed448", EdECObjectIdentifiers.id_Ed448)
                EcCurve.X448 -> Pair("X448", EdECObjectIdentifiers.id_X448)
                else -> throw IllegalStateException()
            }
            val keyFactory = KeyFactory.getInstance(ids.first)
            keyFactory.generatePrivate(
                PKCS8EncodedKeySpec(
                    PrivateKeyInfo(
                        AlgorithmIdentifier(ids.second),
                        DEROctetString(this.d)
                    ).encoded
                )
            )
        }
    }


