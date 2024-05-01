package com.android.identity.crypto

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jcajce.spec.XDHParameterSpec
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.TimeUnit

class CertificateTests {
    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    private fun createKeyPair(curve: EcCurve): KeyPair {
        val stdName =
            when (curve) {
                EcCurve.P256 -> "secp256r1"
                EcCurve.P384 -> "secp384r1"
                EcCurve.P521 -> "secp521r1"
                EcCurve.BRAINPOOLP256R1 -> "brainpoolP256r1"
                EcCurve.BRAINPOOLP320R1 -> "brainpoolP320r1"
                EcCurve.BRAINPOOLP384R1 -> "brainpoolP384r1"
                EcCurve.BRAINPOOLP512R1 -> "brainpoolP512r1"
                EcCurve.X25519 -> "X25519"
                EcCurve.ED25519 -> "Ed25519"
                EcCurve.X448 -> "X448"
                EcCurve.ED448 -> "Ed448"
            }
        return try {
            val kpg: KeyPairGenerator
            if (stdName == "X25519") {
                kpg =
                    KeyPairGenerator.getInstance(
                        "X25519",
                        BouncyCastleProvider.PROVIDER_NAME,
                    )
                kpg.initialize(XDHParameterSpec(XDHParameterSpec.X25519))
            } else if (stdName == "Ed25519") {
                kpg =
                    KeyPairGenerator.getInstance(
                        "Ed25519",
                        BouncyCastleProvider.PROVIDER_NAME,
                    )
            } else if (stdName == "X448") {
                kpg =
                    KeyPairGenerator.getInstance(
                        "X448",
                        BouncyCastleProvider.PROVIDER_NAME,
                    )
                kpg.initialize(XDHParameterSpec(XDHParameterSpec.X448))
            } else if (stdName == "Ed448") {
                kpg =
                    KeyPairGenerator.getInstance(
                        "Ed448",
                        BouncyCastleProvider.PROVIDER_NAME,
                    )
            } else {
                kpg =
                    KeyPairGenerator.getInstance(
                        "EC",
                        BouncyCastleProvider.PROVIDER_NAME,
                    )
                kpg.initialize(ECGenParameterSpec(stdName))
            }
            kpg.generateKeyPair()
        } catch (e: Exception) {
            throw IllegalStateException("Error generating ephemeral key-pair", e)
        }
    }

    private fun testCurve(curve: EcCurve) {
        // Generate an X.509 certificate for all supported curves and check we correcty
        // retrieve the curve information from the resulting certificate.
        val attestationKey = createKeyPair(EcCurve.P384)
        val attestationKeySignatureAlgorithm = "SHA384withECDSA"
        val keyPair = createKeyPair(curve)

        var issuer = X500Name("CN=testIssuer")
        var subject = X500Name("CN=testSubject")
        var validFrom = Date()
        var validUntil = Date(Date().time + TimeUnit.MILLISECONDS.convert(365, TimeUnit.DAYS))
        val serial = BigInteger.ONE
        val certBuilder =
            JcaX509v3CertificateBuilder(
                issuer,
                serial,
                validFrom,
                validUntil,
                subject,
                keyPair.public,
            )
        val signer =
            JcaContentSignerBuilder(attestationKeySignatureAlgorithm)
                .build(attestationKey.private)
        val encodedX509Cert = certBuilder.build(signer).encoded

        // Checks the decoded curve is correct.
        val cert = Certificate(encodedX509Cert)
        val publicKey = cert.publicKey
        Assert.assertEquals(curve, publicKey.curve)

        // Checks the key material is correct.
        Assert.assertEquals(publicKey.javaPublicKey, keyPair.public)

        val pemEncoded = cert.toPem()
        val cert2 = Certificate.fromPem(pemEncoded)
        Assert.assertEquals(cert2, cert)
        Assert.assertEquals(cert2.javaX509Certificate, cert.javaX509Certificate)
    }

    @Test fun testCurve_P256() = testCurve(EcCurve.P256)

    @Test fun testCurve_P384() = testCurve(EcCurve.P384)

    @Test fun testCurve_P521() = testCurve(EcCurve.P521)

    @Test fun testCurve_BRAINPOOLP256R1() = testCurve(EcCurve.BRAINPOOLP256R1)

    @Test fun testCurve_BRAINPOOLP320R1() = testCurve(EcCurve.BRAINPOOLP320R1)

    @Test fun testCurve_BRAINPOOLP384R1() = testCurve(EcCurve.BRAINPOOLP384R1)

    @Test fun testCurve_BRAINPOOLP512R1() = testCurve(EcCurve.BRAINPOOLP512R1)

    @Test fun testCurve_ED25519() = testCurve(EcCurve.ED25519)

    @Test fun testCurve_X25519() = testCurve(EcCurve.X25519)

    @Test fun testCurve_ED448() = testCurve(EcCurve.ED448)

    @Test fun testCurve_X448() = testCurve(EcCurve.X448)
}
