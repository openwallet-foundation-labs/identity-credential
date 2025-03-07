@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.multipaz.crypto

import org.multipaz.util.UUID
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Cryptographic support routines.
 *
 * This object contains various cryptographic primitives and is a wrapper to a platform-
 * specific crypto library.
 */
expect object Crypto {

    /**
     * The Elliptic Curve Cryptography curves supported by the platform.
     */
    val supportedCurves: Set<EcCurve>

    /**
     * Message digest function.
     *
     * @param algorithm must one of [Algorithm.SHA256], [Algorithm.SHA384], [Algorithm.SHA512].
     * @param message the message to get a digest of.
     * @return the digest.
     * @throws IllegalArgumentException if the given algorithm is not supported.
     */
    fun digest(
        algorithm: Algorithm,
        message: ByteArray
    ): ByteArray

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
    fun mac(
        algorithm: Algorithm,
        key: ByteArray,
        message: ByteArray
    ): ByteArray

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
    fun encrypt(
        algorithm: Algorithm,
        key: ByteArray,
        nonce: ByteArray,
        messagePlaintext: ByteArray,
    ): ByteArray

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
    fun decrypt(
        algorithm: Algorithm,
        key: ByteArray,
        nonce: ByteArray,
        messageCiphertext: ByteArray,
    ): ByteArray

    /**
     * Computes an HKDF.
     *
     * @param algorithm must be one of [Algorithm.HMAC_SHA256], [Algorithm.HMAC_SHA384], [Algorithm.HMAC_SHA512].
     * @param ikm the input keying material.
     * @param salt optional salt. A possibly non-secret random value. If no salt is provided (ie. if
     * salt has length 0) then an array of 0s of the same size as the hash digest is used as salt.
     * @param info optional context and application specific information.
     * @param size the length of the generated pseudorandom string in bytes. The maximal
     * size is DigestSize, where DigestSize is the size of the underlying HMAC.
     * @return size pseudorandom bytes.
     */
    fun hkdf(
        algorithm: Algorithm,
        ikm: ByteArray,
        salt: ByteArray?,
        info: ByteArray?,
        size: Int
    ): ByteArray

    /**
     * Checks signature validity.
     *
     * @param publicKey the public key the signature was made with.
     * @param message the data that was signed.
     * @param algorithm the signature algorithm to use.
     * @param signature the signature.
     */
    fun checkSignature(
        publicKey: EcPublicKey,
        message: ByteArray,
        algorithm: Algorithm,
        signature: EcSignature
    ): Boolean

    /**
     * Creates an EC private key.
     *
     * @param curve the curve to use.
     */
    fun createEcPrivateKey(curve: EcCurve): EcPrivateKey

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
    fun sign(
        key: EcPrivateKey,
        signatureAlgorithm: Algorithm,
        message: ByteArray
    ): EcSignature

    /**
     * Performs Key Agreement.
     *
     * @param key the key to use for key agreement.
     * @param otherKey the key from the other party.
     */
    fun keyAgreement(
        key: EcPrivateKey,
        otherKey: EcPublicKey
    ): ByteArray

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
        aad: ByteArray
    ): Pair<ByteArray, EcPublicKey>

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
    ): ByteArray

    internal fun ecPublicKeyToPem(publicKey: EcPublicKey): String

    internal fun ecPublicKeyFromPem(pemEncoding: String, curve: EcCurve): EcPublicKey

    internal fun ecPrivateKeyToPem(privateKey: EcPrivateKey): String

    internal fun ecPrivateKeyFromPem(pemEncoding: String, publicKey: EcPublicKey): EcPrivateKey

    internal fun uuidGetRandom(): UUID

    // TODO: replace with non-platform specific code
    internal fun validateCertChain(certChain: X509CertChain): Boolean

    // TODO: replace with non-platform specific code
    internal fun encryptJwtEcdhEs(
        key: EcPublicKey,
        encAlgorithm: Algorithm,
        claims: JsonObject,
        apu: String,
        apv: String
    ): JsonElement

    // TODO: replace with non-platform specific code
    internal fun decryptJwtEcdhEs(
        encryptedJwt: JsonElement,
        recipientKey: EcPrivateKey
    ): JsonObject

    // TODO: replace with non-platform specific code
    internal fun jwsSign(
        key: EcPrivateKey,
        signatureAlgorithm: Algorithm,
        claimsSet: JsonObject,
        type: String?,
        x5c: X509CertChain?
    ): JsonElement

    // TODO: replace with non-platform specific code
    internal fun jwsVerify(
        signedJwt: JsonElement,
        publicKey: EcPublicKey
    )

    // TODO: replace with non-platform specific code
    internal fun jwsGetInfo(
        signedJwt: JsonElement,
    ): JwsInfo
}

internal data class JwsInfo(
    val claimsSet: JsonObject,
    val type: String?,
    val x5c: X509CertChain?
)
