/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.identity.securearea

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.crypto.toEcPublicKey
import com.android.identity.securearea.software.SoftwareCreateKeySettings
import com.android.identity.securearea.software.SoftwareKeyUnlockData
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.security.InvalidKeyException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.Security
import java.security.cert.X509Certificate
import javax.crypto.KeyAgreement

class SoftwareSecureAreaTest {
    lateinit var attestationKey: EcPrivateKey
    lateinit var attestationKeySignatureAlgorithm: Algorithm
    lateinit var attestationKeyIssuer: String
    lateinit var attestationKeyCertification: CertificateChain

    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        // Create an attestation key...
        attestationKey = Crypto.createEcPrivateKey(EcCurve.P256)
        attestationKeyIssuer = "CN=Test Attestation Key"
        attestationKeySignatureAlgorithm = Algorithm.ES256
        val validFrom = Clock.System.now()
        val validUntil = Instant.fromEpochMilliseconds(
            validFrom.toEpochMilliseconds() + 24 * 3600 * 1000
        )
        val certificate = Crypto.createX509v3Certificate(
            attestationKey.publicKey,
            attestationKey,
            null,
            attestationKeySignatureAlgorithm,
            "1",
            "CN=Test Attestation Key",
            "CN=Test Attestation Key",
            validFrom,
            validUntil, setOf(), listOf()
        )
        attestationKeyCertification = CertificateChain(listOf(certificate))
    }

    @Test
    fun testEcKeyDeletion() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)

        // First create the key...
        ks.createKey(
            "testKey",
            CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256)
        )
        val keyInfo = ks.getKeyInfo("testKey")
        val (certificates) = keyInfo.attestation
        Assert.assertTrue(certificates.size >= 1)

        // Now delete it...
        ks.deleteKey("testKey")

        // Now that we know the key doesn't exist, check that ecKeySign() throws
        try {
            ks.sign("testKey", Algorithm.ES256, byteArrayOf(1, 2), null)
            Assert.fail()
        } catch (e: IllegalArgumentException) {
            // Expected path.
        } catch (e: KeyLockedException) {
            throw AssertionError(e)
        }

        // Now delete it again, this should not fail.
        ks.deleteKey("testKey")
    }

    @Test
    fun testEcKeySigning() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        ks.createKey(
            "testKey",
            CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256)
        )
        val keyInfo = ks.getKeyInfo("testKey")
        Assert.assertNotNull(keyInfo)
        Assert.assertTrue(keyInfo.attestation.certificates.size >= 1)
        Assert.assertEquals(setOf(KeyPurpose.SIGN), keyInfo.keyPurposes)
        Assert.assertEquals(EcCurve.P256, keyInfo.publicKey.curve)
        Assert.assertFalse(keyInfo.isPassphraseProtected)
        val dataToSign = byteArrayOf(4, 5, 6)
        val derSignature: ByteArray
        derSignature = try {
            ks.sign("testKey", Algorithm.ES256, dataToSign, null)
        } catch (e: KeyLockedException) {
            throw AssertionError(e)
        }
        Assert.assertTrue(
            Crypto.checkSignature(
                keyInfo.publicKey,
                dataToSign,
                Algorithm.ES256,
                derSignature
            )
        )
    }

    @Test
    fun testEcKeyCreate() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        ks.createKey(
            "testKey",
            CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256)
        )
        val keyInfo = ks.getKeyInfo("testKey")
        Assert.assertNotNull(keyInfo)
        Assert.assertTrue(keyInfo.attestation.certificates.size >= 1)
        Assert.assertEquals(setOf(KeyPurpose.SIGN), keyInfo.keyPurposes)
        Assert.assertEquals(EcCurve.P256, keyInfo.publicKey.curve)
        Assert.assertFalse(keyInfo.isPassphraseProtected)

        // Check the leaf certificate is self-signed.
        Assert.assertTrue(
            keyInfo.attestation.certificates[0]
                .verify(ks.getKeyInfo("testKey").publicKey)
        )
    }

    @Test
    @Throws(KeyLockedException::class)
    fun testEcKeyCreateWithAttestationKey() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        val challenge = byteArrayOf(1, 2, 3)
        ks.createKey(
            "testKey",
            SoftwareCreateKeySettings.Builder(challenge)
                .setAttestationKey(
                    attestationKey,
                    attestationKeySignatureAlgorithm,
                    attestationKeyIssuer,
                    attestationKeyCertification
                )
                .build()
        )
        val keyInfo = ks.getKeyInfo("testKey")
        Assert.assertNotNull(keyInfo)
        Assert.assertTrue(keyInfo.attestation.certificates.size >= 2)
        Assert.assertEquals(setOf(KeyPurpose.SIGN), keyInfo.keyPurposes)
        Assert.assertEquals(EcCurve.P256, keyInfo.publicKey.curve)
        Assert.assertFalse(keyInfo.isPassphraseProtected)

        // Check challenge.
        val cert = keyInfo.attestation.certificates[0].javaX509Certificate
        Assert.assertArrayEquals(challenge, getChallenge(cert))

        // Check the leaf certificate is signed by mAttestationKey.
        Assert.assertTrue(
            keyInfo.attestation.certificates[0]
                .verify(attestationKey.publicKey)
        )
    }

    @Test
    fun testEcKeyWithGenericCreateKeySettings() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        val challenge = byteArrayOf(1, 2, 3)
        ks.createKey("testKey", CreateKeySettings(challenge, setOf(KeyPurpose.SIGN), EcCurve.P256))
        val keyInfo = ks.getKeyInfo("testKey")
        Assert.assertNotNull(keyInfo)
        Assert.assertTrue(keyInfo.attestation.certificates.size >= 1)
        Assert.assertEquals(setOf(KeyPurpose.SIGN), keyInfo.keyPurposes)
        Assert.assertEquals(EcCurve.P256, keyInfo.publicKey.curve)
        Assert.assertFalse(keyInfo.isPassphraseProtected)

        // Check challenge.
        val cert = keyInfo.attestation.certificates[0].javaX509Certificate
        Assert.assertArrayEquals(challenge, getChallenge(cert))
    }

    @Test
    fun testEcKeySigningWithKeyWithoutCorrectPurpose() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        ks.createKey(
            "testKey",
            SoftwareCreateKeySettings.Builder(ByteArray(0))
                .setKeyPurposes(setOf(KeyPurpose.AGREE_KEY))
                .build()
        )
        val dataToSign = byteArrayOf(4, 5, 6)
        try {
            ks.sign("testKey", Algorithm.ES256, dataToSign, null)
            Assert.fail("Signing shouldn't work with a key w/o purpose SIGN")
        } catch (e: IllegalArgumentException) {
            Assert.assertEquals("Key does not have purpose SIGN", e.message)
        } catch (e: KeyLockedException) {
            throw AssertionError(e)
        }
    }

    @Test
    fun testEcdh() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        val otherKeyPair: KeyPair
        otherKeyPair = try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256)
            kpg.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError("Unexpected exception", e)
        }
        ks.createKey(
            "testKey",
            SoftwareCreateKeySettings.Builder(ByteArray(0))
                .setKeyPurposes(setOf(KeyPurpose.AGREE_KEY))
                .build()
        )
        val keyInfo = ks.getKeyInfo("testKey")
        Assert.assertNotNull(keyInfo)
        Assert.assertTrue(keyInfo.attestation.certificates.size >= 1)
        Assert.assertEquals(setOf(KeyPurpose.AGREE_KEY), keyInfo.keyPurposes)
        Assert.assertEquals(EcCurve.P256, keyInfo.publicKey.curve)
        Assert.assertFalse(keyInfo.isPassphraseProtected)

        // First do the ECDH from the perspective of our side...
        val ourSharedSecret: ByteArray
        ourSharedSecret = try {
            ks.keyAgreement(
                "testKey",
                otherKeyPair.public.toEcPublicKey(EcCurve.P256),
                null
            )
        } catch (e: KeyLockedException) {
            throw AssertionError(e)
        }
        val cert =
            keyInfo.attestation.certificates[0].javaX509Certificate

        // ...now do it from the perspective of the other side...
        val theirSharedSecret: ByteArray
        theirSharedSecret = try {
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(otherKeyPair.private)
            ka.doPhase(cert.publicKey, true)
            ka.generateSecret()
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError("Unexpected exception", e)
        } catch (e: InvalidKeyException) {
            throw AssertionError("Unexpected exception", e)
        }

        // ... finally, check that both sides compute the same shared secret.
        Assert.assertArrayEquals(theirSharedSecret, ourSharedSecret)
    }

    @Test
    fun testEcdhAndSigning() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        val otherKeyPair: KeyPair
        otherKeyPair = try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256)
            kpg.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError("Unexpected exception", e)
        }
        ks.createKey(
            "testKey",
            SoftwareCreateKeySettings.Builder(ByteArray(0))
                .setKeyPurposes(setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY))
                .build()
        )
        val keyInfo = ks.getKeyInfo("testKey")
        Assert.assertNotNull(keyInfo)
        Assert.assertTrue(keyInfo.attestation.certificates.size >= 1)
        Assert.assertEquals(setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY), keyInfo.keyPurposes)
        Assert.assertEquals(EcCurve.P256, keyInfo.publicKey.curve)
        Assert.assertFalse(keyInfo.isPassphraseProtected)

        // First do the ECDH from the perspective of our side...
        val ourSharedSecret: ByteArray
        ourSharedSecret = try {
            ks.keyAgreement(
                "testKey",
                otherKeyPair.public.toEcPublicKey(EcCurve.P256),
                null
            )
        } catch (e: KeyLockedException) {
            throw AssertionError(e)
        }
        val cert = keyInfo.attestation.certificates[0].javaX509Certificate

        // ...now do it from the perspective of the other side...
        val theirSharedSecret: ByteArray
        theirSharedSecret = try {
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(otherKeyPair.private)
            ka.doPhase(cert.publicKey, true)
            ka.generateSecret()
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError("Unexpected exception", e)
        } catch (e: InvalidKeyException) {
            throw AssertionError("Unexpected exception", e)
        }

        // ... finally, check that both sides compute the same shared secret.
        Assert.assertArrayEquals(theirSharedSecret, ourSharedSecret)
        val dataToSign = byteArrayOf(4, 5, 6)
        val derSignature: ByteArray
        derSignature = try {
            ks.sign("testKey", Algorithm.ES256, dataToSign, null)
        } catch (e: KeyLockedException) {
            throw AssertionError(e)
        }
        Assert.assertTrue(
            Crypto.checkSignature(
                keyInfo.publicKey,
                dataToSign,
                Algorithm.ES256,
                derSignature
            )
        )
    }

    @Test
    fun testEcdhWithoutCorrectPurpose() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        val otherKeyPair: KeyPair
        otherKeyPair = try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256)
            kpg.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError("Unexpected exception", e)
        }
        ks.createKey(
            "testKey",
            SoftwareCreateKeySettings.Builder(ByteArray(0)) //.setKeyPurposes(setOf(KeyPurpose.AGREE_KEY))
                .build()
        )
        try {
            ks.keyAgreement(
                "testKey",
                otherKeyPair.public.toEcPublicKey(EcCurve.P256),
                null
            )
            Assert.fail("ECDH shouldn't work with a key w/o purpose AGREE_KEY")
        } catch (e: KeyLockedException) {
            throw AssertionError(e)
        } catch (e: IllegalArgumentException) {
            Assert.assertEquals("Key does not have purpose AGREE_KEY", e.message)
        }
    }

    @Test
    fun testEcKeySigningWithLockedKey() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        val passphrase = "verySekrit"
        ks.createKey(
            "testKey",
            SoftwareCreateKeySettings.Builder(ByteArray(0))
                .setPassphraseRequired(true, passphrase)
                .build()
        )
        val keyInfo = ks.getKeyInfo("testKey")
        Assert.assertNotNull(keyInfo)
        Assert.assertTrue(keyInfo.attestation.certificates.size >= 1)
        Assert.assertEquals(setOf(KeyPurpose.SIGN), keyInfo.keyPurposes)
        Assert.assertEquals(EcCurve.P256, keyInfo.publicKey.curve)
        Assert.assertTrue(keyInfo.isPassphraseProtected)
        val dataToSign = byteArrayOf(4, 5, 6)
        var derSignature = ByteArray(0)
        try {
            derSignature = ks.sign(
                "testKey",
                Algorithm.ES256,
                dataToSign,
                null
            )
            Assert.fail()
        } catch (e: KeyLockedException) {
            // This is the expected path.
        }

        // Try with the wrong passphrase. This should fail.
        try {
            derSignature = ks.sign(
                "testKey",
                Algorithm.ES256,
                dataToSign,
                SoftwareKeyUnlockData("wrongPassphrase")
            )
            Assert.fail()
        } catch (e: KeyLockedException) {
            // This is the expected path.
        }

        // ... and with the right passphrase. This should work.
        derSignature = try {
            ks.sign(
                "testKey",
                Algorithm.ES256,
                dataToSign,
                SoftwareKeyUnlockData(passphrase)
            )
        } catch (e: KeyLockedException) {
            throw AssertionError(e)
        }

        // Verify the signature is correct.
        Assert.assertTrue(
            Crypto.checkSignature(
                keyInfo.publicKey,
                dataToSign,
                Algorithm.ES256,
                derSignature
            )
        )
    }

    @Test
    fun testEcKeyCreationOverridesExistingAlias() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        ks.createKey(
            "testKey",
            CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256)
        )
        val keyInfoOld = ks.getKeyInfo("testKey")
        val certChainOld = keyInfoOld.attestation
        Assert.assertTrue(certChainOld.certificates.size >= 1)
        ks.createKey(
            "testKey",
            CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256)
        )
        val keyInfo = ks.getKeyInfo("testKey")
        val certChain = keyInfo.attestation
        Assert.assertTrue(certChain.certificates.size >= 1)
        val dataToSign = byteArrayOf(4, 5, 6)
        var derSignature = ByteArray(0)
        derSignature = try {
            ks.sign("testKey", Algorithm.ES256, dataToSign, null)
        } catch (e: KeyLockedException) {
            throw AssertionError(e)
        }

        // Check new key is a different cert chain.
        Assert.assertNotEquals(certChainOld, certChain)

        // Check new key is used to sign.
        Assert.assertTrue(
            Crypto.checkSignature(
                keyInfo.publicKey,
                dataToSign,
                Algorithm.ES256,
                derSignature
            )
        )
    }

    @Test
    fun testEcKeySigningAllCurves() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        val knownEcCurves = arrayOf(
            EcCurve.P256,
            EcCurve.P384,
            EcCurve.P521,
            EcCurve.BRAINPOOLP256R1,
            EcCurve.BRAINPOOLP320R1,
            EcCurve.BRAINPOOLP384R1,
            EcCurve.BRAINPOOLP512R1,
            EcCurve.ED25519,
            EcCurve.ED448
        )
        for (ecCurve in knownEcCurves) {
            ks.createKey(
                "testKey",
                SoftwareCreateKeySettings.Builder(ByteArray(0))
                    .setEcCurve(ecCurve)
                    .build()
            )
            val keyInfo = ks.getKeyInfo("testKey")
            Assert.assertNotNull(keyInfo)
            Assert.assertTrue(keyInfo.attestation.certificates.size >= 1)
            Assert.assertEquals(setOf(KeyPurpose.SIGN), keyInfo.keyPurposes)
            Assert.assertEquals(ecCurve, keyInfo.publicKey.curve)
            Assert.assertFalse(keyInfo.isPassphraseProtected)
            val signatureAlgorithms = when (ecCurve) {
                EcCurve.P256,
                EcCurve.P384,
                EcCurve.P521,
                EcCurve.BRAINPOOLP256R1,
                EcCurve.BRAINPOOLP320R1,
                EcCurve.BRAINPOOLP384R1,
                EcCurve.BRAINPOOLP512R1 -> {
                        arrayOf(
                            Algorithm.ES256,
                            Algorithm.ES384,
                            Algorithm.ES512
                        )
                }
                EcCurve.ED25519,
                EcCurve.ED448 -> {
                    arrayOf(Algorithm.EDDSA)
                }
                else -> throw AssertionError()
            }
            for (signatureAlgorithm in signatureAlgorithms) {
                val dataToSign = byteArrayOf(4, 5, 6)
                val derSignature = try {
                    ks.sign("testKey", signatureAlgorithm, dataToSign, null)
                } catch (e: KeyLockedException) {
                    throw AssertionError(e)
                }
                Assert.assertTrue(
                    Crypto.checkSignature(
                        keyInfo.publicKey,
                        dataToSign,
                        signatureAlgorithm,
                        derSignature
                    )
                )
            }
        }
    }

    @Test
    fun testEcKeyEcdhAllCurves() {
        val storage = EphemeralStorageEngine()
        val ks = SoftwareSecureArea(storage)
        val curvesSupportingKeyAgreement = arrayOf(
            EcCurve.P256,
            EcCurve.P384,
            EcCurve.P521,
            EcCurve.BRAINPOOLP256R1,
            EcCurve.BRAINPOOLP320R1,
            EcCurve.BRAINPOOLP384R1,
            EcCurve.BRAINPOOLP512R1,
            EcCurve.X25519,
            EcCurve.X448
        )
        for (ecCurve in curvesSupportingKeyAgreement) {
            var otherKey = Crypto.createEcPrivateKey(ecCurve)

            ks.createKey(
                "testKey",
                SoftwareCreateKeySettings.Builder(ByteArray(0))
                    .setKeyPurposes(setOf(KeyPurpose.AGREE_KEY))
                    .setEcCurve(ecCurve)
                    .setAttestationKey(
                        attestationKey,
                        attestationKeySignatureAlgorithm,
                        attestationKeyIssuer,
                        attestationKeyCertification
                    )
                    .build()
            )
            val keyInfo = ks.getKeyInfo("testKey")
            Assert.assertNotNull(keyInfo)
            Assert.assertTrue(keyInfo.attestation.certificates.size >= 1)
            Assert.assertEquals(setOf(KeyPurpose.AGREE_KEY), keyInfo.keyPurposes)
            Assert.assertEquals(ecCurve, keyInfo.publicKey.curve)
            Assert.assertFalse(keyInfo.isPassphraseProtected)

            // First do the ECDH from the perspective of our side...
            var ourSharedSecret: ByteArray
            ourSharedSecret = try {
                ks.keyAgreement(
                    "testKey",
                    otherKey.publicKey,
                    null
                )
            } catch (e: KeyLockedException) {
                throw AssertionError(e)
            }
            val cert = keyInfo.attestation.certificates[0].javaX509Certificate

            // ...now do it from the perspective of the other side...
            var theirSharedSecret = Crypto.keyAgreement(otherKey, keyInfo.publicKey)

            // ... finally, check that both sides compute the same shared secret.
            Assert.assertArrayEquals(theirSharedSecret, ourSharedSecret)
        }
    }

    companion object {
        fun getChallenge(cert: X509Certificate): ByteArray {
            val octetString = cert.getExtensionValue(AttestationExtension.ATTESTATION_OID)
            return try {
                val asn1InputStream = ASN1InputStream(octetString)
                val encodedCbor = (asn1InputStream.readObject() as ASN1OctetString).octets
                AttestationExtension.decode(encodedCbor)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
}
