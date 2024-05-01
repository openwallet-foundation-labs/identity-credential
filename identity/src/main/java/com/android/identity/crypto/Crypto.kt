package com.android.identity.crypto

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.proto.HpkeAead
import com.google.crypto.tink.proto.HpkeKdf
import com.google.crypto.tink.proto.HpkeKem
import com.google.crypto.tink.proto.HpkeParams
import com.google.crypto.tink.proto.HpkePrivateKey
import com.google.crypto.tink.proto.HpkePublicKey
import com.google.crypto.tink.proto.KeyData
import com.google.crypto.tink.proto.KeyStatusType
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.proto.OutputPrefixType
import com.google.crypto.tink.shaded.protobuf.ByteString
import com.google.crypto.tink.subtle.EllipticCurves
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteStringBuilder
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.agreement.X448Agreement
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.Ed448KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X448KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.Ed448KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed448PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed448PublicKeyParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.params.X448KeyGenerationParameters
import org.bouncycastle.crypto.params.X448PrivateKeyParameters
import org.bouncycastle.crypto.params.X448PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.signers.Ed448Signer
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.BigIntegers
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic support routines.
 *
 * This object contains various cryptographic primitives and is a wrapper to a platform-
 * specific crypto library.
 */
object Crypto {
    /**
     * Message digest function.
     *
     * @param algorithm must one of [Algorithm.SHA256], [Algorithm.SHA384], [Algorithm.SHA512].
     * @param message the message to get a digest of.
     * @return the digest.
     * @throws IllegalArgumentException if the given algorithm is not supported.
     */
    @JvmStatic
    fun digest(
        algorithm: Algorithm,
        message: ByteArray,
    ): ByteArray {
        val algName =
            when (algorithm) {
                Algorithm.SHA256 -> "SHA-256"
                Algorithm.SHA384 -> "SHA-384"
                Algorithm.SHA512 -> "SHA-512"
                else -> {
                    throw IllegalArgumentException("Unsupported algorithm $algorithm")
                }
            }
        return MessageDigest.getInstance(algName).digest(message)
    }

    /**
     * Message authentication code function.
     *
     * @param algorithm must be one of [Algorithm.HMAC_SHA256], [Algorithm.HMAC_SHA384],
     * [Algorithm.HMAC_SHA512].
     * @param key the secret key.
     * @param message the message to authenticate.
     * @return the message authentication code.
     * @throws IllegalArgumentException if the given algorithm is not supported.
     */
    @JvmStatic
    fun mac(
        algorithm: Algorithm,
        key: ByteArray,
        message: ByteArray,
    ): ByteArray {
        val algName =
            when (algorithm) {
                Algorithm.HMAC_SHA256 -> "HmacSha256"
                Algorithm.HMAC_SHA384 -> "HmacSha384"
                Algorithm.HMAC_SHA512 -> "HmacSha512"
                else -> {
                    throw IllegalArgumentException("Unsupported algorithm $algorithm")
                }
            }

        return Mac.getInstance(algName).run {
            init(SecretKeySpec(key, ""))
            update(message)
            doFinal()
        }
    }

    /**
     * Message encryption.
     *
     * @param algorithm must be one of [Algorithm.A128GCM], [Algorithm.A192GCM],
     * [Algorithm.A256GCM].
     * @param key the encryption key.
     * @param nonce the nonce/IV.
     * @param messagePlaintext the message to encrypt.
     * @return the cipher text with the tag appended to it.
     * @throws IllegalArgumentException if the given algorithm is not supported.
     */
    @JvmStatic
    fun encrypt(
        algorithm: Algorithm,
        key: ByteArray,
        nonce: ByteArray,
        messagePlaintext: ByteArray,
    ): ByteArray {
        when (algorithm) {
            Algorithm.A128GCM -> {}
            Algorithm.A192GCM -> {}
            Algorithm.A256GCM -> {}
            else -> {
                throw IllegalArgumentException("Unsupported algorithm $algorithm")
            }
        }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            doFinal(messagePlaintext)
        }
    }

    /**
     * Message decryption.
     *
     * @param algorithm must be one of [Algorithm.A128GCM], [Algorithm.A192GCM],
     * [Algorithm.A256GCM].
     * @param key the encryption key.
     * @param nonce the nonce/IV.
     * @param messageCiphertext the message to decrypt with the tag at the end.
     * @return the plaintext.
     * @throws IllegalArgumentException if the given algorithm is not supported.
     * @throws IllegalStateException if decryption fails
     */
    @JvmStatic
    fun decrypt(
        algorithm: Algorithm,
        key: ByteArray,
        nonce: ByteArray,
        messageCiphertext: ByteArray,
    ): ByteArray {
        when (algorithm) {
            Algorithm.A128GCM -> {}
            Algorithm.A192GCM -> {}
            Algorithm.A256GCM -> {}
            else -> {
                throw IllegalArgumentException("Unsupported algorithm $algorithm")
            }
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(128, nonce),
                )
                doFinal(messageCiphertext)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Error decrypting", e)
        }
    }

    /**
     * Computes an HKDF.
     *
     * @param macAlgorithm must be one of [Algorithm.A128GCM], [Algorithm.A192GCM], [Algorithm.A256GCM].
     * @param ikm the input keying material.
     * @param salt optional salt. A possibly non-secret random value. If no salt is provided (ie. if
     * salt has length 0) then an array of 0s of the same size as the hash digest is used as salt.
     * @param info optional context and application specific information.
     * @param size the length of the generated pseudorandom string in bytes. The maximal
     * size is DigestSize, where DigestSize is the size of the underlying HMAC.
     * @return size pseudorandom bytes.
     */
    @JvmStatic
    fun hkdf(
        algorithm: Algorithm,
        ikm: ByteArray,
        salt: ByteArray?,
        info: ByteArray?,
        size: Int,
    ): ByteArray {
        // This function is based on
        //
        //  https://github.com/google/tink/blob/master/java/src/main/java/com/google/crypto/tink/subtle/Hkdf.java
        //
        val macLength =
            when (algorithm) {
                Algorithm.HMAC_SHA256 -> 32
                Algorithm.HMAC_SHA384 -> 48
                Algorithm.HMAC_SHA512 -> 64
                else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
            }
        require(size <= 255 * macLength) { "size too large" }
        val key =
            if (salt == null || salt.size == 0) {
                // According to RFC 5869, Section 2.2 the salt is optional. If no salt is provided
                // then HKDF uses a salt that is an array of zeros of the same length as the hash
                // digest.
                ByteArray(macLength)
            } else {
                salt
            }
        val prk = mac(algorithm, key, ikm)
        val result = ByteArray(size)
        var ctr = 1
        var pos = 0
        var digest = ByteArray(0)
        while (true) {
            val bsb = ByteStringBuilder()
            bsb.append(digest, 0, digest.size)
            if (info != null) {
                bsb.append(info, 0, info.size)
            }
            bsb.append(ctr.toByte())
            digest = mac(algorithm, prk, bsb.toByteString().toByteArray())
            if (pos + digest.size < size) {
                System.arraycopy(digest, 0, result, pos, digest.size)
                pos += digest.size
                ctr++
            } else {
                System.arraycopy(digest, 0, result, pos, size - pos)
                break
            }
        }
        return result
    }

    /**
     * Checks signature validity.
     *
     * The signature must be DER encoded except for curve Ed25519 and Ed448 where it
     * should just be the raw R and S values.
     *
     * @param publicKey the public key the signature was made with.
     * @param message the data that was signed.
     * @param algorithm the signature algorithm to use.
     * @param signature the signature.
     */
    @JvmStatic
    fun checkSignature(
        publicKey: EcPublicKey,
        message: ByteArray,
        algorithm: Algorithm,
        signature: ByteArray,
    ): Boolean {
        val signatureAlgorithm =
            when (algorithm) {
                Algorithm.UNSET -> throw IllegalArgumentException("Algorithm not set")
                Algorithm.ES256 -> "SHA256withECDSA"
                Algorithm.ES384 -> "SHA384withECDSA"
                Algorithm.ES512 -> "SHA512withECDSA"
                Algorithm.EDDSA -> {
                    when (publicKey.curve) {
                        EcCurve.ED25519 -> "Ed25519"
                        EcCurve.ED448 -> "Ed448"
                        else -> throw IllegalArgumentException(
                            "Algorithm $algorithm incompatible " +
                                "with curve ${publicKey.curve}",
                        )
                    }
                }

                else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
            }

        return try {
            Signature.getInstance(signatureAlgorithm).run {
                initVerify(publicKey.javaPublicKey)
                update(message)
                verify(signature)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Error verifying signature", e)
        }
    }

    /**
     * Creates an EC private key.
     *
     * @param curve the curve to use.
     */
    @JvmStatic
    fun createEcPrivateKey(curve: EcCurve): EcPrivateKey =
        when (curve) {
            EcCurve.P256,
            EcCurve.P384,
            EcCurve.P521,
            EcCurve.BRAINPOOLP256R1,
            EcCurve.BRAINPOOLP320R1,
            EcCurve.BRAINPOOLP384R1,
            EcCurve.BRAINPOOLP512R1,
            -> {
                val kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
                kpg.initialize(ECGenParameterSpec(curve.SECGName))
                val keyPair = kpg.generateKeyPair()
                val publicKey = keyPair.public.toEcPublicKey(curve)
                check(publicKey is EcPublicKeyDoubleCoordinate)
                val d = (keyPair.private as BCECPrivateKey).d.toByteArray()
                EcPrivateKeyDoubleCoordinate(curve, d, publicKey.x, publicKey.y)
            }

            EcCurve.ED25519 -> {
                Ed25519KeyPairGenerator().run {
                    init(Ed25519KeyGenerationParameters(SecureRandom()))
                    generateKeyPair().run {
                        val privateKey = this.private as Ed25519PrivateKeyParameters
                        val publicKey = this.public as Ed25519PublicKeyParameters
                        EcPrivateKeyOkp(curve, privateKey.encoded, publicKey.encoded)
                    }
                }
            }

            EcCurve.X25519 -> {
                X25519KeyPairGenerator().run {
                    init(X25519KeyGenerationParameters(SecureRandom()))
                    generateKeyPair().run {
                        val privateKey = this.private as X25519PrivateKeyParameters
                        val publicKey = this.public as X25519PublicKeyParameters
                        EcPrivateKeyOkp(curve, privateKey.encoded, publicKey.encoded)
                    }
                }
            }

            EcCurve.ED448 -> {
                Ed448KeyPairGenerator().run {
                    init(Ed448KeyGenerationParameters(SecureRandom()))
                    generateKeyPair().run {
                        val privateKey = this.private as Ed448PrivateKeyParameters
                        val publicKey = this.public as Ed448PublicKeyParameters
                        EcPrivateKeyOkp(curve, privateKey.encoded, publicKey.encoded)
                    }
                }
            }

            EcCurve.X448 -> {
                X448KeyPairGenerator().run {
                    init(X448KeyGenerationParameters(SecureRandom()))
                    generateKeyPair().run {
                        val privateKey = this.private as X448PrivateKeyParameters
                        val publicKey = this.public as X448PublicKeyParameters
                        EcPrivateKeyOkp(curve, privateKey.encoded, publicKey.encoded)
                    }
                }
            }
        }

    /**
     * Signs data with a key.
     *
     * The signature is DER encoded except for curve Ed25519 and Ed448 where it's just
     * the raw R and S values.
     *
     * @param key the key to sign with.
     * @param signatureAlgorithm the signature algorithm to use.
     * @param message the data to sign.
     * @return the signature.
     */
    @JvmStatic
    fun sign(
        key: EcPrivateKey,
        signatureAlgorithm: Algorithm,
        message: ByteArray,
    ): ByteArray =
        when (key.curve) {
            EcCurve.P256,
            EcCurve.P384,
            EcCurve.P521,
            EcCurve.BRAINPOOLP256R1,
            EcCurve.BRAINPOOLP320R1,
            EcCurve.BRAINPOOLP384R1,
            EcCurve.BRAINPOOLP512R1,
            -> {
                val signatureAlgorithmName =
                    when (signatureAlgorithm) {
                        Algorithm.ES256 -> "SHA256withECDSA"
                        Algorithm.ES384 -> "SHA384withECDSA"
                        Algorithm.ES512 -> "SHA512withECDSA"
                        else -> throw IllegalArgumentException(
                            "Unsupported signing algorithm $signatureAlgorithm for curve ${key.curve}",
                        )
                    }
                val spec =
                    ECPrivateKeySpec(
                        BigIntegers.fromUnsignedByteArray(key.d),
                        ECNamedCurveTable.getParameterSpec(key.curve.SECGName),
                    )
                val kf = KeyFactory.getInstance("EC")
                val privateKey = kf.generatePrivate(spec)
                try {
                    Signature.getInstance(signatureAlgorithmName, BouncyCastleProvider.PROVIDER_NAME)
                        .run {
                            initSign(privateKey)
                            update(message)
                            sign()
                        }
                } catch (e: Exception) {
                    throw IllegalStateException("Unexpected Exception", e)
                }
            }

            EcCurve.ED25519 -> {
                val privateKey = Ed25519PrivateKeyParameters(key.d, 0)
                Ed25519Signer().run {
                    init(true, privateKey)
                    update(message, 0, message.size)
                    generateSignature()
                }
            }

            EcCurve.ED448 -> {
                val privateKey = Ed448PrivateKeyParameters(key.d, 0)
                Ed448Signer(byteArrayOf()).run {
                    init(true, privateKey)
                    update(message, 0, message.size)
                    generateSignature()
                }
            }

            EcCurve.X25519,
            EcCurve.X448,
            -> {
                throw IllegalStateException("Key with curve ${key.curve} does not support signing")
            }
        }

    /**
     * Performs Key Agreement.
     *
     * @param key the key to use for key agreement.
     * @param otherKey the key from the other party.
     */
    @JvmStatic
    fun keyAgreement(
        key: EcPrivateKey,
        otherKey: EcPublicKey,
    ): ByteArray =
        when (key.curve) {
            EcCurve.P256,
            EcCurve.P384,
            EcCurve.P521,
            EcCurve.BRAINPOOLP256R1,
            EcCurve.BRAINPOOLP320R1,
            EcCurve.BRAINPOOLP384R1,
            EcCurve.BRAINPOOLP512R1,
            -> {
                ECPrivateKeySpec(
                    BigIntegers.fromUnsignedByteArray(key.d),
                    ECNamedCurveTable.getParameterSpec(key.curve.SECGName),
                ).run {
                    val kf = KeyFactory.getInstance("EC")
                    val privateKey = kf.generatePrivate(this)
                    try {
                        val ka = KeyAgreement.getInstance("ECDH")
                        ka.init(privateKey)
                        ka.doPhase(otherKey.javaPublicKey, true)
                        ka.generateSecret()
                    } catch (e: Exception) {
                        throw IllegalStateException("Unexpected Exception", e)
                    }
                }
            }

            EcCurve.ED25519,
            EcCurve.ED448,
            -> {
                throw IllegalStateException("Key with curve ${key.curve} does not support key-agreement")
            }

            EcCurve.X25519 -> {
                check(otherKey is EcPublicKeyOkp)
                val otherKeyX = X25519PublicKeyParameters(otherKey.x, 0)
                val privateKey = X25519PrivateKeyParameters(key.d, 0)
                val ka = X25519Agreement()
                ka.init(privateKey)
                val buf = ByteArray(ka.agreementSize)
                ka.calculateAgreement(otherKeyX, buf, 0)
                buf
            }

            EcCurve.X448 -> {
                check(otherKey is EcPublicKeyOkp)
                val otherKeyX = X448PublicKeyParameters(otherKey.x, 0)
                val privateKey = X448PrivateKeyParameters(key.d, 0)
                val ka = X448Agreement()
                ka.init(privateKey)
                val buf = ByteArray(ka.agreementSize)
                ka.calculateAgreement(otherKeyX, buf, 0)
                buf
            }
        }

    /**
     * Creates a certificate for a public key.
     *
     * @param publicKey the key to create a certificate for.
     * @param signingKey the key to sign the certificate.
     * @param signingKeyCertificate the certificate for the signing key, if available.
     * @param signatureAlgorithm the signature algorithm to use.
     * @param serial the serial, must be a number.
     * @param subject the subject string.
     * @param issuer the issuer string.
     * @param validFrom when the certificate should be valid from.
     * @param validUntil when the certificate should be valid until.
     * @param options one or more options from the [CreateCertificateOption] enumeration.
     * @param additionalExtensions additional extensions to put into the certificate.
     */
    @JvmStatic
    fun createX509v3Certificate(
        publicKey: EcPublicKey,
        signingKey: EcPrivateKey,
        signingKeyCertificate: Certificate?,
        signatureAlgorithm: Algorithm,
        serial: String,
        subject: String,
        issuer: String,
        validFrom: Instant,
        validUntil: Instant,
        options: Set<CreateCertificateOption>,
        additionalExtensions: List<X509v3Extension>,
    ): Certificate {
        val signatureAlgorithmString =
            when (signatureAlgorithm) {
                Algorithm.ES256 -> "SHA256withECDSA"
                Algorithm.ES384 -> "SHA384withECDSA"
                Algorithm.ES512 -> "SHA512withECDSA"
                Algorithm.EDDSA -> {
                    when (signingKey.curve) {
                        EcCurve.ED25519 -> "Ed25519"
                        EcCurve.ED448 -> "Ed448"
                        else -> throw IllegalArgumentException(
                            "ALGORITHM_EDDSA can only be used with Ed25519 and Ed448",
                        )
                    }
                }

                else -> throw IllegalArgumentException("Algorithm cannot be used for signing")
            }
        val certSigningKeyJava = signingKey.javaPrivateKey

        val publicKeyJava = publicKey.javaPublicKey
        val certBuilder =
            JcaX509v3CertificateBuilder(
                X500Name(issuer),
                BigInteger(serial),
                Date(validFrom.toEpochMilliseconds()),
                Date(validUntil.toEpochMilliseconds()),
                X500Name(subject),
                publicKeyJava,
            )
        if (options.contains(CreateCertificateOption.INCLUDE_SUBJECT_KEY_IDENTIFIER)) {
            certBuilder.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                JcaX509ExtensionUtils().createSubjectKeyIdentifier(publicKeyJava),
            )
        }
        if (options.contains(CreateCertificateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_AS_SUBJECT_KEY_IDENTIFIER)) {
            certBuilder.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                JcaX509ExtensionUtils().createAuthorityKeyIdentifier(publicKeyJava),
            )
        }
        if (options.contains(
                CreateCertificateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_FROM_SIGNING_KEY_CERTIFICATE,
            )
        ) {
            check(signingKeyCertificate != null)
            val signerCert = signingKeyCertificate.javaX509Certificate
            val encoded = signerCert.getExtensionValue(Extension.subjectKeyIdentifier.toString())
            val subjectKeyIdentifier = SubjectKeyIdentifier.getInstance(ASN1OctetString.getInstance(encoded).octets)
            certBuilder.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                AuthorityKeyIdentifier(subjectKeyIdentifier.keyIdentifier),
            )
        }
        additionalExtensions.forEach { extension ->
            certBuilder.addExtension(
                ASN1ObjectIdentifier(extension.oid),
                extension.isCritical,
                extension.payload,
            )
        }
        val signer = JcaContentSignerBuilder(signatureAlgorithmString).build(certSigningKeyJava)
        val encodedCert: ByteArray = certBuilder.build(signer).getEncoded()
        val cf = CertificateFactory.getInstance("X.509")
        val bais = ByteArrayInputStream(encodedCert)
        val x509cert = cf.generateCertificate(bais) as X509Certificate
        return Certificate(x509cert.encoded)
    }

    init {
        TinkConfig.register()
        HybridConfig.register()
    }

    private fun hpkeGetKeysetHandles(
        publicKey: EcPublicKey,
        privateKey: EcPrivateKey?,
    ): Pair<KeysetHandle, KeysetHandle?> {
        val primaryKeyId = 1

        val params =
            HpkeParams.newBuilder()
                .setAead(HpkeAead.AES_128_GCM)
                .setKdf(HpkeKdf.HKDF_SHA256)
                .setKem(HpkeKem.DHKEM_P256_HKDF_SHA256)
                .build()

        val javaPublicKey = publicKey.javaPublicKey as ECPublicKey
        val encodedKey =
            EllipticCurves.pointEncode(
                EllipticCurves.CurveType.NIST_P256,
                EllipticCurves.PointFormatType.UNCOMPRESSED,
                javaPublicKey.w,
            )

        val hpkePublicKey =
            HpkePublicKey.newBuilder()
                .setVersion(0)
                .setPublicKey(ByteString.copyFrom(encodedKey))
                .setParams(params)
                .build()

        val publicKeyData =
            KeyData.newBuilder()
                .setKeyMaterialType(KeyData.KeyMaterialType.ASYMMETRIC_PUBLIC)
                .setTypeUrl("type.googleapis.com/google.crypto.tink.HpkePublicKey")
                .setValue(hpkePublicKey.toByteString())
                .build()

        val publicKeysetKey =
            Keyset.Key.newBuilder()
                .setKeyId(primaryKeyId)
                .setKeyData(publicKeyData)
                .setOutputPrefixType(OutputPrefixType.RAW)
                .setStatus(KeyStatusType.ENABLED)
                .build()

        val publicKeyset =
            Keyset.newBuilder()
                .setPrimaryKeyId(primaryKeyId)
                .addKey(publicKeysetKey)
                .build()

        val publicKeysetHandle =
            TinkProtoKeysetFormat.parseKeyset(
                publicKeyset.toByteArray(),
                InsecureSecretKeyAccess.get(),
            )
        var privateKeysetHandle: KeysetHandle? = null

        if (privateKey != null) {
            val javaPrivateKey = privateKey.javaPrivateKey as ECPrivateKey
            val hpkePrivateKey =
                HpkePrivateKey.newBuilder()
                    .setPublicKey(hpkePublicKey)
                    .setPrivateKey(ByteString.copyFrom(javaPrivateKey.s.toByteArray()))
                    .build()

            val privateKeyData =
                KeyData.newBuilder()
                    .setKeyMaterialType(KeyData.KeyMaterialType.ASYMMETRIC_PRIVATE)
                    .setTypeUrl("type.googleapis.com/google.crypto.tink.HpkePrivateKey")
                    .setValue(hpkePrivateKey.toByteString())
                    .build()

            val privateKeysetKey =
                Keyset.Key.newBuilder()
                    .setKeyId(primaryKeyId)
                    .setKeyData(privateKeyData)
                    .setOutputPrefixType(OutputPrefixType.RAW)
                    .setStatus(KeyStatusType.ENABLED)
                    .build()
            val privateKeyset =
                Keyset.newBuilder()
                    .setPrimaryKeyId(primaryKeyId)
                    .addKey(privateKeysetKey)
                    .build()
            privateKeysetHandle =
                TinkProtoKeysetFormat.parseKeyset(
                    privateKeyset.toByteArray(),
                    InsecureSecretKeyAccess.get(),
                )
        }

        return Pair(publicKeysetHandle, privateKeysetHandle)
    }

    /**
     * Encrypts data using HPKE according to [RFC 9180](https://datatracker.ietf.org/doc/rfc9180/).
     *
     * The resulting ciphertext and encapsulated key should be sent to the receiver and both
     * parties must also agree on the AAD used.
     *
     * @param cipherSuite the cipher suite for selecting the KEM, KDF, and encryption algorithm.
     *   Presently only [Algorithm.HPKE_BASE_P256_SHA256_AES128GCM] is supported.
     * @param receiverPublicKey the public key of the receiver, curve must match the cipher suite.
     * @param plainText the data to encrypt.
     * @param aad additional authenticated data.
     * @return the ciphertext and the encapsulated key.
     */
    fun hpkeEncrypt(
        cipherSuite: Algorithm,
        receiverPublicKey: EcPublicKey,
        plainText: ByteArray,
        aad: ByteArray,
    ): Pair<ByteArray, EcPublicKey> {
        require(cipherSuite == Algorithm.HPKE_BASE_P256_SHA256_AES128GCM) {
            "Only HPKE_BASE_P256_SHA256_AES128GCM is supported right now"
        }
        require(receiverPublicKey.curve == EcCurve.P256)

        val (publicKeysetHandle, _) = hpkeGetKeysetHandles(receiverPublicKey, null)

        val encryptor = publicKeysetHandle.getPrimitive(HybridEncrypt::class.java)
        val output = encryptor.encrypt(plainText, aad)

        // Output from Tink is (serialized encapsulated key || ciphertext) so we need to break it
        // up ourselves

        receiverPublicKey as EcPublicKeyDoubleCoordinate
        val coordinateSize = (receiverPublicKey.curve.bitSize + 7) / 8
        val encapsulatedPublicKeySize = 1 + 2 * coordinateSize

        val encapsulatedKey =
            EcPublicKeyDoubleCoordinate.fromUncompressedPointEncoding(
                EcCurve.P256,
                output.sliceArray(IntRange(0, encapsulatedPublicKeySize - 1)),
            )
        val encryptedData = output.sliceArray(IntRange(encapsulatedPublicKeySize, output.size - 1))

        return Pair(encryptedData, encapsulatedKey)
    }

    /**
     * Decrypts data using HPKE according to [RFC 9180](https://datatracker.ietf.org/doc/rfc9180/).
     *
     * The ciphertext and encapsulated key should be received from the sender and both parties
     * must also agree on the AAD to use.
     *
     * @param cipherSuite the cipher suite for selecting the KEM, KDF, and encryption algorithm.
     *   Presently only [Algorithm.HPKE_BASE_P256_SHA256_AES128GCM] is supported.
     * @param receiverPrivateKey the private key of the receiver, curve must match the cipher suite.
     * @param cipherText the data to decrypt.
     * @param aad additional authenticated data.
     * @param encapsulatedPublicKey the encapsulated key.
     * @return the plaintext.
     */
    fun hpkeDecrypt(
        cipherSuite: Algorithm,
        receiverPrivateKey: EcPrivateKey,
        cipherText: ByteArray,
        aad: ByteArray,
        encapsulatedPublicKey: EcPublicKey,
    ): ByteArray {
        require(cipherSuite == Algorithm.HPKE_BASE_P256_SHA256_AES128GCM) {
            "Only HPKE_BASE_P256_SHA256_AES128GCM is supported right now"
        }
        require(receiverPrivateKey.curve == EcCurve.P256)
        encapsulatedPublicKey as EcPublicKeyDoubleCoordinate

        val (_, privateKeysetHandle) =
            hpkeGetKeysetHandles(receiverPrivateKey.publicKey, receiverPrivateKey)

        val decryptor = privateKeysetHandle!!.getPrimitive(HybridDecrypt::class.java)

        // Tink expects the input to be (serialized encapsulated key || ciphertext)
        return decryptor.decrypt(encapsulatedPublicKey.asUncompressedPointEncoding + cipherText, aad)
    }
}
