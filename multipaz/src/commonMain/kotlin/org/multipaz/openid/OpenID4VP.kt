package org.multipaz.openid

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.JsonWebSignature
import org.multipaz.crypto.X509CertChain

object OpenID4VP {

    /**
     * Generates an OpenID4VP 1.0 request.
     *
     * @param origin the origin, e.g. `https://verifier.multipaz.org`.
     * @param clientId the client ID, e.g. `x509_san_dns:verifier.multipaz.org`.
     * @param nonce the nonce to use.
     * @param responseEncryptionKey the key to encrypt the response against or `null`.
     * @param requestSigningKey the key to sign the request with or `null`.
     * @param requestSigningKeyCertification the certification for [requestSigningKey] or `null`.
     * @param dclqQuery the DCQL query.
     * @return the OpenID4VP request.
     */
    fun generateRequest(
        origin: String,
        clientId: String,
        nonce: String,
        responseEncryptionKey: EcPublicKey?,
        requestSigningKey: EcPrivateKey?,
        requestSigningKeyCertification: X509CertChain?,
        dclqQuery: JsonObject
    ): JsonObject {
        val unsignedRequest = buildJsonObject {
            put("response_type", "vp_token")
            put("response_mode", if (responseEncryptionKey != null) "dc_api.jwt" else "dc_api")
            if (requestSigningKey != null) {
                put("client_id", clientId)
                putJsonArray("expected_origins") {
                    add(origin)
                }
            }
            put("dcql_query", dclqQuery)
            put("nonce", nonce)
            putJsonObject("client_metadata") {
                // TODO: take parameters for all these
                put("vp_formats_supported", buildJsonObject {
                    putJsonObject("mso_mdoc") {
                        putJsonArray("issuerauth_alg_values") {
                            add(Algorithm.ESP256.coseAlgorithmIdentifier)
                            add(Algorithm.ES256.coseAlgorithmIdentifier)
                        }
                        putJsonArray("deviceauth_alg_values") {
                            add(Algorithm.ESP256.coseAlgorithmIdentifier)
                            add(Algorithm.ES256.coseAlgorithmIdentifier)
                        }
                    }
                    putJsonObject("dc+sd-jwt") {
                        putJsonArray("sd-jwt_alg_values") {
                            add(Algorithm.ESP256.joseAlgorithmIdentifier)
                        }
                        putJsonArray("kb-jwt_alg_values") {
                            add(Algorithm.ESP256.joseAlgorithmIdentifier)
                        }
                    }
                })
                if (responseEncryptionKey != null) {
                    putJsonObject("jwks") {
                        putJsonArray("keys") {
                            add(responseEncryptionKey
                                .toJwk(additionalClaims = buildJsonObject {
                                    put("kid", "response-encryption-key")
                                    put("alg", "ECDH-ES")
                                }))
                        }
                    }
                }
            }
        }
        if (requestSigningKey == null) {
            return unsignedRequest
        }
        return buildJsonObject {
            put("request", JsonPrimitive(JsonWebSignature.sign(
                key = requestSigningKey,
                signatureAlgorithm = requestSigningKey.curve.defaultSigningAlgorithmFullySpecified,
                claimsSet = unsignedRequest,
                type = "oauth-authz-req+jwt",
                x5c = requestSigningKeyCertification
            )))
        }
    }


    /**
     * Generates an OpenID4VP request for Draft 24.
     *
     * @param origin the origin, e.g. `https://verifier.multipaz.org`.
     * @param clientId the client ID, e.g. `x509_san_dns:verifier.multipaz.org`.
     * @param nonce the nonce to use.
     * @param responseEncryptionKey the key to encrypt the response against or `null`.
     * @param requestSigningKey the key to sign the request with or `null`.
     * @param requestSigningKeyCertification the certification for [requestSigningKey] or `null`.
     * @param dclqQuery the DCQL query.
     * @return the OpenID4VP request.
     */
    fun generateRequestDraft24(
        origin: String,
        clientId: String,
        nonce: String,
        responseEncryptionKey: EcPublicKey?,
        requestSigningKey: EcPrivateKey?,
        requestSigningKeyCertification: X509CertChain?,
        dclqQuery: JsonObject
    ): JsonObject {
        val unsignedRequest = buildJsonObject {
            put("response_type", "vp_token")
            put("response_mode", if (responseEncryptionKey != null) "dc_api.jwt" else "dc_api")
            if (requestSigningKey != null) {
                put("client_id", clientId)
                putJsonArray("expected_origins") {
                    add(origin)
                }
            }
            put("dcql_query", dclqQuery)
            put("nonce", nonce)
            putJsonObject("client_metadata") {
                put("vp_formats", buildJsonObject {
                    putJsonObject("mso_mdoc") {
                        putJsonArray("alg") {
                            add("ES256")
                        }
                    }
                    putJsonObject("dc+sd-jwt") {
                        putJsonArray("sd-jwt_alg_values") {
                            add("ES256")
                        }
                        putJsonArray("kb-jwt_alg_values") {
                            add("ES256")
                        }
                    }
                })
                if (responseEncryptionKey != null) {
                    put("authorization_encrypted_response_alg", "ECDH-ES")
                    put("authorization_encrypted_response_enc", "A128GCM")
                    putJsonObject("jwks") {
                        putJsonArray("keys") {
                            add(
                                responseEncryptionKey
                                .toJwk(additionalClaims = buildJsonObject {
                                    put("kid", "response-encryption-key")
                                })
                            )
                        }
                    }
                }
            }
        }
        if (requestSigningKey == null) {
            return unsignedRequest
        }
        return buildJsonObject {
            put("request", JsonPrimitive(JsonWebSignature.sign(
                key = requestSigningKey,
                signatureAlgorithm = requestSigningKey.curve.defaultSigningAlgorithmFullySpecified,
                claimsSet = unsignedRequest,
                type = "oauth-authz-req+jwt",
                x5c = requestSigningKeyCertification
            )))
        }
    }
}