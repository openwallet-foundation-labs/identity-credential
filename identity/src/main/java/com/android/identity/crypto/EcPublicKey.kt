package com.android.identity.crypto

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseKey
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.toCoseLabel
import kotlinx.io.bytestring.ByteStringBuilder
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCXDHPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
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
    fun toPem(): String {
        val sb = StringBuilder()
        sb.append("-----BEGIN PUBLIC KEY-----\n")
        sb.append(Base64.Mime.encode(javaPublicKey.encoded))
        sb.append("\n-----END PUBLIC KEY-----\n")
        return sb.toString()
    }

    val toDataItem: DataItem
        get() {
            return toCoseKey().toDataItem
        }

    companion object {
        /**
         * Creates an [EcPublicKey] from a PEM encoded string.
         *
         * @param pemEncoding the PEM encoded string.
         * @param curve the curve of the key..
         * @return a new [EcPublicKey].
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun fromPem(pemEncoding: String, curve: EcCurve): EcPublicKey {
            val encoded = Base64.Mime.decode(pemEncoding
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .trim())
            val kf = when (curve) {
                EcCurve.ED448,
                EcCurve.ED25519 -> KeyFactory.getInstance("EdDSA", BouncyCastleProvider.PROVIDER_NAME)
                EcCurve.X25519,
                EcCurve.X448 -> KeyFactory.getInstance("XDH", BouncyCastleProvider.PROVIDER_NAME)
                else -> KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
            }
            val spec = X509EncodedKeySpec(encoded)
            val publicKeyJava = kf.generatePublic(spec)
            return publicKeyJava.toEcPublicKey(curve)
        }

        /**
         * Gets a [EcPublicKey] from a COSE Key.
         *
         * @param coseKey the COSE Key.
         * @return the public key.
         */
        fun fromCoseKey(coseKey: CoseKey): EcPublicKey =
            when (coseKey.keyType) {
                Cose.COSE_KEY_TYPE_EC2.toDataItem -> {
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

                Cose.COSE_KEY_TYPE_OKP.toDataItem -> {
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

// TODO: move to identity-jvm library

fun PublicKey.toEcPublicKey(curve: EcCurve): EcPublicKey =
    when (curve) {
        EcCurve.X25519,
        EcCurve.X448 -> {
            EcPublicKeyOkp(
                curve,
                (this as BCXDHPublicKey).uEncoding
            )
        }

        EcCurve.ED25519,
        EcCurve.ED448 -> {
            EcPublicKeyOkp(
                curve,
                (this as BCEdDSAPublicKey).pointEncoding
            )
        }

        else -> {
            check(this is ECPublicKey)
            val keySizeOctets = (curve.bitSize + 7) / 8
            EcPublicKeyDoubleCoordinate(
                curve,
                BigIntegers.asUnsignedByteArray(keySizeOctets, this.w.affineX),
                BigIntegers.asUnsignedByteArray(keySizeOctets, this.w.affineY)
            )
        }
    }

private val ED25519_X509_ENCODED_PREFIX =
    byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
private val X25519_X509_ENCODED_PREFIX =
    byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00)
private val ED448_X509_ENCODED_PREFIX =
    byteArrayOf(0x30, 0x43, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x71, 0x03, 0x3a, 0x00)
private val X448_X509_ENCODED_PREFIX =
    byteArrayOf(0x30, 0x42, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6f, 0x03, 0x39, 0x00)

val EcPublicKey.javaPublicKey: PublicKey
    get() = when (this) {
        is EcPublicKeyDoubleCoordinate -> {
            val keySizeOctets = (curve.bitSize + 7)/8
            check(x.size == keySizeOctets)
            check(y.size == keySizeOctets)
            val bx = BigInteger(1, x)
            val by = BigInteger(1, y)
            try {
                val params = AlgorithmParameters.getInstance(
                    "EC",
                    BouncyCastleProvider.PROVIDER_NAME
                )
                params.init(ECGenParameterSpec(curve.SECGName))
                val ecParameters = params.getParameterSpec(ECParameterSpec::class.java)
                val ecPoint = ECPoint(bx, by)
                val keySpec = ECPublicKeySpec(ecPoint, ecParameters)
                val kf = KeyFactory.getInstance("EC")
                kf.generatePublic(keySpec)
            } catch (e: Exception) {
                throw IllegalStateException("Unexpected error", e)
            }
        }

        is EcPublicKeyOkp -> {
            try {
                // Unfortunately we need to create an X509 encoded version of the public
                // key material, for simplicity we just use prefixes.
                val prefix: ByteArray
                val kf: KeyFactory
                when (curve) {
                    EcCurve.ED448 -> {
                        kf = KeyFactory.getInstance("EdDSA", BouncyCastleProvider.PROVIDER_NAME)
                        prefix = ED448_X509_ENCODED_PREFIX
                    }

                    EcCurve.ED25519 -> {
                        kf = KeyFactory.getInstance("EdDSA", BouncyCastleProvider.PROVIDER_NAME)
                        prefix = ED25519_X509_ENCODED_PREFIX
                    }

                    EcCurve.X25519 -> {
                        kf = KeyFactory.getInstance("XDH", BouncyCastleProvider.PROVIDER_NAME)
                        prefix = X25519_X509_ENCODED_PREFIX
                    }

                    EcCurve.X448 -> {
                        kf = KeyFactory.getInstance("XDH", BouncyCastleProvider.PROVIDER_NAME)
                        prefix = X448_X509_ENCODED_PREFIX
                    }

                    else -> throw IllegalArgumentException("Unsupported curve with id $curve")
                }
                val bsb = ByteStringBuilder()
                bsb.append(prefix)
                bsb.append(x)
                kf.generatePublic(X509EncodedKeySpec(bsb.toByteString().toByteArray()))
            } catch (e: Exception) {
                // any exception, such as NoSuchAlgorithmException, InvalidKeySpecException, IOException, NoSuchProviderException
                throw IllegalStateException("Unexpected error", e)
            }
        }
    }

