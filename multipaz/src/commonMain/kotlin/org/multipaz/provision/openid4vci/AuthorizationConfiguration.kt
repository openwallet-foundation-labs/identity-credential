package org.multipaz.provision.openid4vci

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.multipaz.crypto.Algorithm
import org.multipaz.rpc.backend.BackendEnvironment

internal data class AuthorizationConfiguration(
    val pushedAuthorizationRequestEndpoint: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val dpopSigningAlgorithm: Algorithm,
    val useClientAssertion: Boolean
) {
    companion object: JsonParsing("Authorization server metadata") {
        suspend fun get(url: String, clientPreferences: Openid4VciClientPreferences): AuthorizationConfiguration {
            val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!

            // Fetch issuer metadata
            val metadataUrl = wellKnown(url, "oauth-authorization-server")
            val metadataRequest = httpClient.get(metadataUrl) {}
            if (metadataRequest.status != HttpStatusCode.OK) {
                throw IllegalStateException("Invalid issuer, no $metadataUrl")
            }
            val metadataText = metadataRequest.readBytes().decodeToString()
            val metadata = Json.parseToJsonElement(metadataText).jsonObject
            val authorizationEndpoint = metadata.string("authorization_endpoint")
            val parEndpoint = metadata.string("pushed_authorization_request_endpoint")
            val tokenEndpoint = metadata.string("token_endpoint")

            val responseType = metadata.arrayOrNull("response_types_supported")
            if (responseType != null) {
                var codeSupported = false
                for (response in responseType) {
                    if (response is JsonPrimitive && response.content == "code") {
                        codeSupported = true
                        break
                    }
                }
                if (!codeSupported) {
                    throw IllegalStateException("response type 'code' is not supported")
                }
            }
            val codeChallengeMethods = metadata.array("code_challenge_methods_supported")
            if (responseType != null) {
                var challengeSupported = false
                for (method in codeChallengeMethods) {
                    if (method is JsonPrimitive && method.content == "S256") {
                        challengeSupported = true
                        break
                    }
                }
                if (!challengeSupported) {
                    throw IllegalStateException("challenge type 'S256' is not supported")
                }
            }

            val dpopSigningAlgorithm = preferredAlgorithm(
                available = metadata.arrayOrNull("dpop_signing_alg_values_supported"),
                clientPreferences = clientPreferences)
            val authMethods = metadata.arrayOrNull("token_endpoint_auth_methods_supported")
            var requireClientAssertion = false
            if (authMethods != null) {
                // Normally we send client attestation, but if server requests it, we can do
                // client assertion too.
                // TODO: see what other types (if any) we may want to support.
                for (authMethod in authMethods) {
                    if (authMethod is JsonPrimitive && authMethod.content == "private_key_jwt") {
                        requireClientAssertion = true
                        break
                    }
                }
            }
            return AuthorizationConfiguration(
                pushedAuthorizationRequestEndpoint = parEndpoint,
                authorizationEndpoint = authorizationEndpoint,
                tokenEndpoint = tokenEndpoint,
                dpopSigningAlgorithm = dpopSigningAlgorithm,
                useClientAssertion = requireClientAssertion
            )
        }
    }
}