package com.android.identity.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Security

class EcPrivateKeyTests {

    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    fun createAndCheck(curve: EcCurve) {
        val privateKey = Crypto.createEcPrivateKey(curve)

        val (signatureAlgorithm, keyAgreement) = when (curve) {
            EcCurve.P256 -> Pair(Algorithm.ES256, true)
            EcCurve.P384 -> Pair(Algorithm.ES384, true)
            EcCurve.P521 -> Pair(Algorithm.ES512, true)
            EcCurve.BRAINPOOLP256R1 -> Pair(Algorithm.ES256, true)
            EcCurve.BRAINPOOLP320R1 -> Pair(Algorithm.ES256, true)
            EcCurve.BRAINPOOLP384R1 -> Pair(Algorithm.ES384, true)
            EcCurve.BRAINPOOLP512R1 -> Pair(Algorithm.ES512, true)
            EcCurve.ED25519 -> Pair(Algorithm.EDDSA, false)
            EcCurve.X25519 -> Pair(Algorithm.UNSET, true)
            EcCurve.ED448 -> Pair(Algorithm.EDDSA, false)
            EcCurve.X448 -> Pair(Algorithm.UNSET, true)
        }

        // Check that serialization works
        Assert.assertEquals(privateKey.toCoseKey().ecPrivateKey, privateKey)

        if (signatureAlgorithm != Algorithm.UNSET) {
            val message = "Hello World".toByteArray()
            val derSignature =
                Crypto.sign(privateKey, signatureAlgorithm, message)
            val signatureValid = Crypto.checkSignature(
                privateKey.publicKey,
                message,
                signatureAlgorithm,
                derSignature
            )
            Assert.assertTrue(signatureValid)
        }
        if (keyAgreement) {
            val otherKey = Crypto.createEcPrivateKey(curve)
            val Zab = Crypto.keyAgreement(privateKey, otherKey.publicKey)
            // TODO: add test-cases with vectors to check correctness. This just checks
            //  that no exception is thrown and the operation completes, not that the
            //  Zab value is correct.
        }
    }

    @Test fun createAndCheck_P256() = createAndCheck(EcCurve.P256)
    @Test fun createAndCheck_P384() = createAndCheck(EcCurve.P384)
    @Test fun createAndCheck_P521() = createAndCheck(EcCurve.P521)
    @Test fun createAndCheck_BRAINPOOLP256R1() = createAndCheck(EcCurve.BRAINPOOLP256R1)
    @Test fun createAndCheck_BRAINPOOLP320R1() = createAndCheck(EcCurve.BRAINPOOLP320R1)
    @Test fun createAndCheck_BRAINPOOLP384R1() = createAndCheck(EcCurve.BRAINPOOLP384R1)
    @Test fun createAndCheck_BRAINPOOLP512R1() = createAndCheck(EcCurve.BRAINPOOLP512R1)
    @Test fun createAndCheck_ED25519() = createAndCheck(EcCurve.ED25519)
    @Test fun createAndCheck_X25519() = createAndCheck(EcCurve.X25519)
    @Test fun createAndCheck_ED448() = createAndCheck(EcCurve.ED448)
    @Test fun createAndCheck_X448() = createAndCheck(EcCurve.X448)

    fun conversion(curve: EcCurve) {
        val key = Crypto.createEcPrivateKey(curve)
        val javaPrivateKey = key.javaPrivateKey
        val keyFromJava = javaPrivateKey.toEcPrivateKey(key.publicKey.javaPublicKey, curve)
        Assert.assertEquals(key, keyFromJava)
    }

    @Test fun conversion_P256() = conversion(EcCurve.P256)
    @Test fun conversion_P384() = conversion(EcCurve.P384)
    @Test fun conversion_P521() = conversion(EcCurve.P521)
    @Test fun conversion_BRAINPOOLP256R1() = conversion(EcCurve.BRAINPOOLP256R1)
    @Test fun conversion_BRAINPOOLP320R1() = conversion(EcCurve.BRAINPOOLP320R1)
    @Test fun conversion_BRAINPOOLP384R1() = conversion(EcCurve.BRAINPOOLP384R1)
    @Test fun conversion_BRAINPOOLP512R1() = conversion(EcCurve.BRAINPOOLP512R1)
    @Test fun conversion_ED25519() = conversion(EcCurve.ED25519)
    @Test fun conversion_X25519() = conversion(EcCurve.X25519)
    @Test fun conversion_ED448() = conversion(EcCurve.ED448)
    @Test fun conversion_X448() = conversion(EcCurve.X448)

}