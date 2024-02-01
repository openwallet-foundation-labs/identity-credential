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
package com.android.identity.securearea.software

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.builder.MapBuilder
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.android.identity.internal.Util.cborEncode
import com.android.identity.internal.Util.cborMapExtractBoolean
import com.android.identity.internal.Util.cborMapExtractByteString
import com.android.identity.internal.Util.cborMapExtractNumber
import com.android.identity.internal.Util.computeHkdf
import com.android.identity.securearea.Algorithm
import com.android.identity.securearea.AttestationExtension
import com.android.identity.securearea.AttestationExtension.encode
import com.android.identity.securearea.EcCurve
import com.android.identity.securearea.KeyLockedException
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.KeyPurpose.Companion.encodeSet
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.securearea.SecureArea
import com.android.identity.storage.StorageEngine
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyAgreement
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * An implementation of [SecureArea] in software.
 *
 * This implementation supports all the curves and algorithms defined by [SecureArea]
 * and also supports passphrase-protected keys. Key material is stored using the
 * [StorageEngine] abstraction and passphrase-protected keys are encrypted using
 * [AES-GCM](https://en.wikipedia.org/wiki/Galois/Counter_Mode)
 * with 256-bit keys with the key derived from the passphrase using
 * [HKDF](https://en.wikipedia.org/wiki/HKDF).
 *
 * This is currently implemented using the
 * [Bouncy Castle](https://www.bouncycastle.org/) library but this implementation
 * detail may change in the future.
 *
 * @param storageEngine the storage engine to use for storing key material.
 */
class SoftwareSecureArea(private val storageEngine: StorageEngine) : SecureArea {
    override val identifier: String
        get() = "SoftwareSecureArea"

    override val displayName: String
        get() = "Software Secure Area"

    override fun createKey(
        alias: String,
        createKeySettings: com.android.identity.securearea.CreateKeySettings
    ) {
        val settings = if (createKeySettings is SoftwareCreateKeySettings) {
            createKeySettings
        } else {
            // Use default settings if user passed in a generic SecureArea.CreateKeySettings.
            SoftwareCreateKeySettings.Builder(createKeySettings.attestationChallenge).build()
        }
        var kpg: KeyPairGenerator
        try {
            kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
            val selfSigningSignatureAlgorithm: String?
            when (settings.ecCurve) {
                EcCurve.P256 -> {
                    kpg.initialize(ECGenParameterSpec("secp256r1"))
                    selfSigningSignatureAlgorithm = "SHA256withECDSA"
                }

                EcCurve.P384 -> {
                    kpg.initialize(ECGenParameterSpec("secp384r1"))
                    selfSigningSignatureAlgorithm = "SHA384withECDSA"
                }

                EcCurve.P521 -> {
                    kpg.initialize(ECGenParameterSpec("secp521r1"))
                    selfSigningSignatureAlgorithm = "SHA512withECDSA"
                }

                EcCurve.BRAINPOOLP256R1 -> {
                    kpg.initialize(ECGenParameterSpec("brainpoolP256r1"))
                    selfSigningSignatureAlgorithm = "SHA256withECDSA"
                }

                EcCurve.BRAINPOOLP320R1 -> {
                    kpg.initialize(ECGenParameterSpec("brainpoolP320r1"))
                    selfSigningSignatureAlgorithm = "SHA256withECDSA"
                }

                EcCurve.BRAINPOOLP384R1 -> {
                    kpg.initialize(ECGenParameterSpec("brainpoolP384r1"))
                    selfSigningSignatureAlgorithm = "SHA384withECDSA"
                }

                EcCurve.BRAINPOOLP512R1 -> {
                    kpg.initialize(ECGenParameterSpec("brainpoolP512r1"))
                    selfSigningSignatureAlgorithm = "SHA512withECDSA"
                }

                EcCurve.ED25519 -> {
                    kpg =
                        KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
                    selfSigningSignatureAlgorithm = "Ed25519"
                }

                EcCurve.ED448 -> {
                    kpg = KeyPairGenerator.getInstance("Ed448", BouncyCastleProvider.PROVIDER_NAME)
                    selfSigningSignatureAlgorithm = "Ed448"
                }

                EcCurve.X25519 -> {
                    kpg = KeyPairGenerator.getInstance("x25519", BouncyCastleProvider.PROVIDER_NAME)
                    selfSigningSignatureAlgorithm = null // Not possible to self-sign
                }

                EcCurve.X448 -> {
                    kpg = KeyPairGenerator.getInstance("x448", BouncyCastleProvider.PROVIDER_NAME)
                    selfSigningSignatureAlgorithm = null // Not possible to self-sign
                }

                else -> throw IllegalArgumentException(
                    "Unknown curve with id " + settings.ecCurve
                )
            }
            val keyPair = kpg.generateKeyPair()
            val builder = CborBuilder()
            val map: MapBuilder<CborBuilder> = builder.addMap()
            map.put("curve", settings.ecCurve.coseCurveIdentifier.toLong())
            map.put("keyPurposes", encodeSet(settings.keyPurposes).toLong())
            map.put("passphraseRequired", settings.passphraseRequired)
            if (!settings.passphraseRequired) {
                map.put("privateKey", keyPair.private.encoded)
            } else {
                val cleartextPrivateKey = keyPair.private.encoded
                val secretKey = derivePrivateKeyEncryptionKey(
                    keyPair.public.encoded,
                    settings.passphrase
                )
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    val baos = ByteArrayOutputStream()
                    baos.write(cipher.iv)
                    baos.write(cipher.doFinal(cleartextPrivateKey))
                    val encryptedPrivateKey = baos.toByteArray()
                    map.put("publicKey", keyPair.public.encoded)
                    map.put("encryptedPrivateKey", encryptedPrivateKey)
                } catch (e: NoSuchPaddingException) {
                    throw IllegalStateException("Error encrypting private key", e)
                } catch (e: IllegalBlockSizeException) {
                    throw IllegalStateException("Error encrypting private key", e)
                } catch (e: BadPaddingException) {
                    throw IllegalStateException("Error encrypting private key", e)
                } catch (e: InvalidKeyException) {
                    throw IllegalStateException("Error encrypting private key", e)
                }
            }
            val subject = X500Name(settings.subject ?: "CN=SoftwareSecureArea Key")
            val issuer: X500Name
            val certSigningKey: PrivateKey?
            val signatureAlgorithm: String?

            // If an attestation key isn't available, self-sign the certificate (if possible)
            if (settings.attestationKey != null) {
                issuer =
                    X500Name(settings.attestationKeyCertification!![0].subjectX500Principal.name)
                certSigningKey = settings.attestationKey
                signatureAlgorithm = settings.attestationKeySignatureAlgorithm
            } else {
                issuer = subject
                certSigningKey = keyPair.private
                checkNotNull(selfSigningSignatureAlgorithm) { "Self-signing not possible with this curve, use an attestation key" }
                signatureAlgorithm = selfSigningSignatureAlgorithm
            }
            var validFrom = Date()
            if (settings.validFrom != null) {
                validFrom = Date(settings.validFrom.toEpochMilli())
            }
            var validUntil = Date(Date().time + TimeUnit.MILLISECONDS.convert(365, TimeUnit.DAYS))
            if (settings.validUntil != null) {
                validUntil = Date(settings.validUntil.toEpochMilli())
            }
            val serial = BigInteger.ONE
            val certBuilder = JcaX509v3CertificateBuilder(
                issuer,
                serial,
                validFrom,
                validUntil,
                subject,
                keyPair.public
            )
            certBuilder.addExtension(
                ASN1ObjectIdentifier(AttestationExtension.ATTESTATION_OID),
                false,
                encode(settings.attestationChallenge)
            )
            val signer = JcaContentSignerBuilder(signatureAlgorithm).build(certSigningKey)
            val encodedCert: ByteArray = certBuilder.build(signer).getEncoded()
            val cf = CertificateFactory.getInstance("X.509")
            val bais = ByteArrayInputStream(encodedCert)
            val certificateChain = ArrayList<X509Certificate>()
            certificateChain.add(cf.generateCertificate(bais) as X509Certificate)
            if (settings.attestationKeyCertification != null) {
                certificateChain.addAll(settings.attestationKeyCertification)
            }
            val attestationBuilder = map.putArray("attestation")
            for (certificate in certificateChain) {
                try {
                    attestationBuilder.add(certificate.encoded)
                } catch (e: CertificateEncodingException) {
                    throw IllegalStateException("Error encoding certificate chain", e)
                }
            }
            attestationBuilder.end()
            storageEngine.put(PREFIX + alias, cborEncode(builder.build().get(0)))
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Unexpected exception", e)
        } catch (e: CertificateException) {
            throw IllegalStateException("Unexpected exception", e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw IllegalStateException("Unexpected exception", e)
        } catch (e: OperatorCreationException) {
            throw IllegalStateException("Unexpected exception", e)
        } catch (e: IOException) {
            throw IllegalStateException("Unexpected exception", e)
        } catch (e: NoSuchProviderException) {
            throw IllegalStateException("Unexpected exception", e)
        }
    }

    private fun derivePrivateKeyEncryptionKey(
        encodedPublicKey: ByteArray,
        passphrase: String
    ): SecretKey {
        val info = "ICPrivateKeyEncryption1".toByteArray(StandardCharsets.UTF_8)
        val derivedKey = computeHkdf(
            "HmacSha256",
            passphrase.toByteArray(StandardCharsets.UTF_8),
            encodedPublicKey,
            info,
            32
        )
        return SecretKeySpec(derivedKey, "AES")
    }

    override fun deleteKey(alias: String) {
        storageEngine.delete(PREFIX + alias)
    }

    private data class KeyData(
        val curve: EcCurve,
        val keyPurposes: Set<KeyPurpose>,
        val privateKey: PrivateKey,
    )

    @Throws(KeyLockedException::class)
    private fun loadKey(
        prefix: String,
        alias: String,
        keyUnlockData: KeyUnlockData?
    ): KeyData {
        var passphrase: String? = null
        if (keyUnlockData != null) {
            val unlockData = keyUnlockData as SoftwareKeyUnlockData
            passphrase = unlockData.passphrase
        }
        val data = storageEngine[prefix + alias]
            ?: throw IllegalArgumentException("No key with given alias")
        val bais = ByteArrayInputStream(data)
        val dataItems = try {
            CborDecoder(bais).decode()
        } catch (e: CborException) {
            throw IllegalStateException("Error decoded CBOR", e)
        }
        check(dataItems.size == 1) { "Expected 1 item, found " + dataItems.size }
        check(dataItems[0] is Map) { "Item is not a map" }
        val map = dataItems[0] as Map
        val curve = EcCurve.fromInt(cborMapExtractNumber(map, "curve").toInt())
        val keyPurposes = KeyPurpose.decodeSet(cborMapExtractNumber(map, "keyPurposes").toInt())
        val encodedPrivateKey: ByteArray
        val passphraseRequired = cborMapExtractBoolean(map, "passphraseRequired")
        encodedPrivateKey = if (passphraseRequired) {
            if (passphrase == null) {
                throw KeyLockedException("No passphrase provided")
            }
            val encodedPublicKey = cborMapExtractByteString(map, "publicKey")
            val encryptedPrivateKey = cborMapExtractByteString(map, "encryptedPrivateKey")
            val secretKey = derivePrivateKeyEncryptionKey(encodedPublicKey, passphrase)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val byteBuffer = ByteBuffer.wrap(encryptedPrivateKey)
                val iv = ByteArray(12)
                byteBuffer[iv]
                val cipherText = ByteArray(encryptedPrivateKey.size - 12)
                byteBuffer[cipherText]
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                cipher.doFinal(cipherText)
            } catch (e: NoSuchAlgorithmException) {
                throw KeyLockedException("Error decrypting private key", e)
            } catch (e: NoSuchPaddingException) {
                throw KeyLockedException("Error decrypting private key", e)
            } catch (e: IllegalBlockSizeException) {
                throw KeyLockedException("Error decrypting private key", e)
            } catch (e: BadPaddingException) {
                throw KeyLockedException("Error decrypting private key", e)
            } catch (e: InvalidKeyException) {
                throw KeyLockedException("Error decrypting private key", e)
            } catch (e: InvalidAlgorithmParameterException) {
                throw KeyLockedException("Error decrypting private key", e)
            }
        } else {
            cborMapExtractByteString(map, "privateKey")
        }
        val encodedKeySpec = PKCS8EncodedKeySpec(encodedPrivateKey)
        val privateKey = try {
            val ecKeyFac = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
            ecKeyFac.generatePrivate(encodedKeySpec)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Error loading private key", e)
        } catch (e: InvalidKeySpecException) {
            throw IllegalStateException("Error loading private key", e)
        } catch (e: NoSuchProviderException) {
            throw IllegalStateException("Error loading private key", e)
        }
        return KeyData(curve, keyPurposes, privateKey)
    }

    /**
     * Gets the underlying private key.
     *
     * @param alias the alias for the key.
     * @param keyUnlockData unlock data, or `null`.
     * @return a [PrivateKey].
     * @throws KeyLockedException
     */
    @Throws(KeyLockedException::class)
    fun getPrivateKey(
        alias: String,
        keyUnlockData: KeyUnlockData?
    ): PrivateKey {
        val keyData = loadKey(PREFIX, alias, keyUnlockData)
        return keyData.privateKey
    }

    @Throws(KeyLockedException::class)
    override fun sign(
        alias: String,
        signatureAlgorithm: Algorithm,
        dataToSign: ByteArray,
        keyUnlockData: KeyUnlockData?
    ): ByteArray {
        val keyData = loadKey(PREFIX, alias, keyUnlockData)
        require(keyData.keyPurposes.contains(KeyPurpose.SIGN)) { "Key does not have purpose SIGN" }
        val signatureAlgorithmName = when (signatureAlgorithm) {
            Algorithm.ES256 -> "SHA256withECDSA"
            Algorithm.ES384 -> "SHA384withECDSA"
            Algorithm.ES512 -> "SHA512withECDSA"
            Algorithm.EDDSA -> if (keyData.curve === EcCurve.ED25519) {
                "Ed25519"
            } else if (keyData.curve === EcCurve.ED448) {
                "Ed448"
            } else {
                throw IllegalArgumentException(
                    "ALGORITHM_EDDSA can only be used with EC_CURVE_ED_25519 and EC_CURVE_ED_448"
                )
            }

            else -> throw IllegalArgumentException(
                "Unsupported signing algorithm  with id $signatureAlgorithm"
            )
        }
        return try {
            val s = Signature.getInstance(signatureAlgorithmName, BouncyCastleProvider.PROVIDER_NAME)
            s.initSign(keyData.privateKey)
            s.update(dataToSign)
            s.sign()
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Unexpected Exception", e)
        } catch (e: SignatureException) {
            throw IllegalStateException("Unexpected Exception", e)
        } catch (e: InvalidKeyException) {
            throw IllegalStateException("Unexpected Exception", e)
        } catch (e: NoSuchProviderException) {
            throw IllegalStateException("Unexpected Exception", e)
        }
    }

    @Throws(KeyLockedException::class)
    override fun keyAgreement(
        alias: String,
        otherKey: PublicKey,
        keyUnlockData: KeyUnlockData?
    ): ByteArray {
        val keyData = loadKey(PREFIX, alias, keyUnlockData)
        require(keyData.keyPurposes.contains(KeyPurpose.AGREE_KEY)) { "Key does not have purpose AGREE_KEY" }
        return try {
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(keyData.privateKey)
            ka.doPhase(otherKey, true)
            ka.generateSecret()
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Unexpected Exception", e)
        } catch (e: InvalidKeyException) {
            throw IllegalStateException("Unexpected Exception", e)
        }
    }

    override fun getKeyInfo(alias: String): SoftwareKeyInfo {
        val data = storageEngine[PREFIX + alias]
            ?: throw IllegalArgumentException("No key with given alias")
        val bais = ByteArrayInputStream(data)
        val dataItems = try {
            CborDecoder(bais).decode()
        } catch (e: CborException) {
            throw IllegalStateException("Error decoded CBOR", e)
        }
        check(dataItems.size == 1) { "Expected 1 item, found " + dataItems.size }
        check(dataItems[0] is Map) { "Item is not a map" }
        val map = dataItems[0] as Map
        val ecCurve = EcCurve.fromInt(cborMapExtractNumber(map, "curve").toInt())
        val keyPurposes = KeyPurpose.decodeSet(cborMapExtractNumber(map, "keyPurposes").toInt())
        val passphraseRequired = cborMapExtractBoolean(map, "passphraseRequired")
        val attestationDataItem: DataItem = map[UnicodeString("attestation")]
        check(attestationDataItem is Array) { "attestation not found or not array" }
        val attestation: MutableList<X509Certificate> = ArrayList()
        for (item in attestationDataItem.dataItems) {
            val encodedCert = (item as ByteString).bytes
            try {
                val cf = CertificateFactory.getInstance("X.509")
                val certBais = ByteArrayInputStream(encodedCert)
                attestation.add(cf.generateCertificate(certBais) as X509Certificate)
            } catch (e: CertificateException) {
                throw IllegalStateException("Error decoding certificate blob", e)
            }
        }
        return SoftwareKeyInfo(
            attestation, keyPurposes, ecCurve, false,
            passphraseRequired
        )
    }

    companion object {
        private const val TAG = "SoftwareSecureArea"

        // Prefix for storage items.
        private const val PREFIX = "IC_SoftwareSecureArea_key_"
    }
}
