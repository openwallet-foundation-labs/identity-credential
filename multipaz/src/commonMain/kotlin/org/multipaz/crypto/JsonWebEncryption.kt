package org.multipaz.crypto

import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 *  JSON Web Encryption (JWE) support routines
 */
object JsonWebEncryption {
    /**
     * Encrypts a claim set using ECDH-ES.
     *
     * Reference: [RFC 7518 Section 4.6 Key Agreement with Elliptic Curve Diffie-Hellman Ephemeral Static (ECDH-ES)](https://datatracker.ietf.org/doc/html/rfc7518#section-4.6)
     *
     * @param claimsSet the claims set to encrypt.
     * @param recipientPublicKey the public key to encrypt to.
     * @param encAlg the encryption algorithm, [Algorithm.A128GCM], [Algorithm.A192GCM], or [Algorithm.A256GCM].
     * @param apu agreement PartyUInfo (apu) parameter, must be base64url encoded.
     * @param apv agreement PartyVInfo (apv) parameter, must be base64url encoded.
     * @return a [JsonElement] with the encrypted JWT.
     */
    fun encrypt(
        claimsSet: JsonObject,
        recipientPublicKey: EcPublicKey,
        encAlg: Algorithm,
        apu: String,
        apv: String
    ): JsonElement {
        return Crypto.encryptJwtEcdhEs(
            key = recipientPublicKey,
            encAlgorithm = encAlg,
            claims = claimsSet,
            apu = apu,
            apv = apv
        )
    }

    /**
     * Decrypts an encrypted JWT using ECDH-ES.
     *
     * Reference: [RFC 7518 Section 4.6 Key Agreement with Elliptic Curve Diffie-Hellman Ephemeral Static (ECDH-ES)](https://datatracker.ietf.org/doc/html/rfc7518#section-4.6)
     *
     * @param encryptedJwt the encrypted JWT.
     * @param recipientKey the recipients private key corresponding to the public key this was encrypted to.
     * @return the decrypted claims set.
     */
    fun decrypt(
        encryptedJwt: JsonElement,
        recipientKey: EcPrivateKey
    ): JsonObject {
        return Crypto.decryptJwtEcdhEs(encryptedJwt, recipientKey)
    }

}