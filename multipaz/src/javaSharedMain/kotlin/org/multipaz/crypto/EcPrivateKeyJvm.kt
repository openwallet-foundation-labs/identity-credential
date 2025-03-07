package org.multipaz.crypto

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


