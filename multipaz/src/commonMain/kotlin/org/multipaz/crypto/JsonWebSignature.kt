package org.multipaz.crypto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JSON Web Signature support
 */
object JsonWebSignature {

    /**
     * Sign a claims set.
     *
     * @param key the key to sign with.
     * @param signatureAlgorithm the signature algorithm to use.
     * @param claimsSet the claims set.
     * @param type the value to put in the "typ" header parameter or `null`.
     * @param x5c: the certificate chain to put in the "x5c" header parameter or `null`.
     * @return a [JsonElement] with the JWS.
     */
    fun sign(
        key: EcPrivateKey,
        signatureAlgorithm: Algorithm,
        claimsSet: JsonObject,
        type: String?,
        x5c: X509CertChain?
    ): JsonElement {
        return Crypto.jwsSign(
            key = key,
            signatureAlgorithm = signatureAlgorithm,
            claimsSet = claimsSet,
            type = type,
            x5c = x5c
        )
    }

    /**
     * Verify the signature of a JWS.
     *
     * @param jws the JWS.
     * @param publicKey the key to use for verification
     * @throws Throwable if verification fails.
     */
    fun verify(
        jws: JsonElement,
        publicKey: EcPublicKey
    ) {
        Crypto.jwsVerify(jws, publicKey)
    }

    /**
     * Information about a JWS.
     *
     * @property claimsSet the claims set being signed.
     * @property type the value of the `typ` header element of `null.
     * @property x5c the certificate chain in the `x5c` header element or `null`.
     */
    data class JwsInfo(
        val claimsSet: JsonObject,
        val type: String?,
        val x5c: X509CertChain?
    )

    /**
     * Get information about a JWS.
     *
     * @param jws the JWS.
     * @return a [JwsInfo] with information about the JWS.
     */
    fun getInfo(jws: JsonElement): JwsInfo {
        val res = Crypto.jwsGetInfo(jws)
        return JwsInfo(
            claimsSet = res.claimsSet,
            type = res.type,
            x5c = res.x5c
        )
    }
}