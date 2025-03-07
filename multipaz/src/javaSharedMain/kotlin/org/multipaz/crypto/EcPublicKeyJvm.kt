package org.multipaz.crypto

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

val EcCurve.requireBouncyCastle: Boolean
    get() = when (this) {
        EcCurve.BRAINPOOLP256R1, EcCurve.BRAINPOOLP384R1,
            EcCurve.BRAINPOOLP320R1, EcCurve.BRAINPOOLP512R1 -> true
        else -> false
    }

val EcCurve.javaKeyAlgorithm: String
    get() = when(this) {
        EcCurve.ED448, EcCurve.ED25519 -> "EdDSA"
        EcCurve.X25519, EcCurve.X448 -> "XDH"
        else -> "EC"
    }

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
                val kf = if (curve.requireBouncyCastle) {
                    KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
                } else {
                    KeyFactory.getInstance("EC")
                }
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

