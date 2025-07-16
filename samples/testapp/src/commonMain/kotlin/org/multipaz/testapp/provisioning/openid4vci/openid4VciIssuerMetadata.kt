package org.multipaz.testapp.provisioning.openid4vci

import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.protocolWithAuthority
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.crypto.Algorithm

// from .well-known/openid-credential-issuer
internal data class Openid4VciIssuerMetadata(
    val credentialIssuerId: String,  // may or may not contain trailing slash
    val credentialIssuer: String,
    val nonceEndpoint: String,
    val credentialEndpoint: String,
    val display: List<Openid4VciIssuerDisplay>,
    val credentialConfigurations: Map<String, Openid4VciCredentialConfiguration>,
    val authorizationServerList: List<Openid4VciAuthorizationMetadata>
) {
    companion object {
        const val TAG = "Openid4VciIssuerMetadata"

        suspend fun get(issuerUrl: String): Openid4VciIssuerMetadata {
            return BackendEnvironment.cache(Openid4VciIssuerMetadata::class, issuerUrl) { _, _ ->
                val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!

                // Fetch issuer metadata
                val issuerMetadataUrl = wellKnownOauth(issuerUrl, "openid-credential-issuer")
                val issuerMetadataRequest = httpClient.get(issuerMetadataUrl) {}
                if (issuerMetadataRequest.status != HttpStatusCode.OK) {
                    throw IllegalStateException("Invalid issuer, no $issuerMetadataUrl")
                }
                val credentialMetadataText = issuerMetadataRequest.readBytes().decodeToString()
                val credentialMetadata = Json.parseToJsonElement(credentialMetadataText).jsonObject

                // Fetch oauth metadata
                val authorizationServers = credentialMetadata["authorization_servers"]?.jsonArray
                val authorizationServerUrls =
                    authorizationServers?.map { it.jsonPrimitive.content } ?: listOf(issuerUrl)
                val authorizationMetadataList = mutableListOf<Openid4VciAuthorizationMetadata>()
                for (authorizationServerUrl in authorizationServerUrls) {
                    val authorizationMetadataUrl =
                        wellKnownOauth(authorizationServerUrl, "oauth-authorization-server")
                    val authorizationMetadataRequest = httpClient.get(authorizationMetadataUrl) {}
                    if (authorizationMetadataRequest.status != HttpStatusCode.OK) {
                        if (authorizationServerUrl != issuerUrl) {
                            Logger.e(TAG, "Invalid authorization server '$authorizationServerUrl'")
                        }
                        continue
                    }
                    val authorizationMetadataText = authorizationMetadataRequest.readBytes().decodeToString()
                    val authorizationMetadata =
                        extractAuthorizationServerMetadata(
                            url = authorizationServerUrl,
                            jsonObject = Json.parseToJsonElement(authorizationMetadataText).jsonObject
                        )
                    if (authorizationMetadata != null) {
                        authorizationMetadataList.add(authorizationMetadata)
                    }
                }
                Openid4VciIssuerMetadata(
                    credentialIssuerId = credentialMetadata["credential_issuer"]?.jsonPrimitive?.content
                        ?: issuerUrl,
                    credentialIssuer = credentialMetadata["credential_issuer"]?.jsonPrimitive?.content ?: issuerUrl,
                    nonceEndpoint = credentialMetadata["nonce_endpoint"]!!.jsonPrimitive.content,
                    credentialEndpoint = credentialMetadata["credential_endpoint"]!!.jsonPrimitive.content,
                    display = extractDisplay(credentialMetadata["display"]),
                    authorizationServerList = authorizationMetadataList.toList(),
                    credentialConfigurations =
                        credentialMetadata["credential_configurations_supported"]!!.jsonObject.mapValues {
                            val obj = it.value.jsonObject
                            Openid4VciCredentialConfiguration(
                                id = it.key,
                                scope = obj["scope"]?.jsonPrimitive?.content,
                                cryptographicBindingMethod = preferred(
                                    obj["cryptographic_binding_methods_supported"]?.jsonArray,
                                    SUPPORTED_BINDING_METHODS
                                ),
                                credentialSigningAlgorithm = preferredAlgorithm(
                                    obj["credential_signing_alg_values_supported"]?.jsonArray
                                        ?: obj["cryptographic_signing_alg_values_supported"]?.jsonArray,  // TODO: old name, to remove
                                    SUPPORTED_SIGNATURE_ALGORITHMS,
                                    Algorithm.ESP256
                                ),
                                proofType = extractProofType(obj["proof_types_supported"]?.jsonObject),
                                format = extractFormat(obj),
                                display = extractDisplay(obj["display"])
                            )
                        }
                )
            }
        }

        // ".well-known/$name" file for the given url according to RFC8414
        private fun wellKnownOauth(url: String, name: String): String {
            val parsedUrl = Url(url)
            val head = parsedUrl.protocolWithAuthority
            val path = parsedUrl.encodedPath
            return "$head/.well-known/$name$path"
        }

        private fun preferred(available: JsonArray?, supported: List<String>): String? {
            val availableSet = available?.map { it.jsonPrimitive.content }?.toSet() ?: return null
            return supported.firstOrNull { availableSet.contains(it) }
        }

        private fun preferredAlgorithm(
            available: JsonArray?,
            supported: List<Algorithm>,
            defaultAlgorithm: Algorithm? = null
        ): Algorithm? {
            if (available == null) {
                if (defaultAlgorithm != null) {
                    Logger.e(TAG, "Expected issuing server metadata missing")
                }
                return defaultAlgorithm
            }
            // Accept both JOSE and COSE identifiers
            val availableJoseSet = available
                .filterIsInstance<JsonPrimitive>()
                .filter { it.isString }
                .map { it.content }
                .toSet()
            val availableCoseSet = available
                .filterIsInstance<JsonPrimitive>()
                .filter { !it.isString }
                .map { it.content.toInt() }
                .toSet()
            return supported.firstOrNull {
                val cose = it.coseAlgorithmIdentifier
                val jose = it.joseAlgorithmIdentifier
                (cose != null && availableCoseSet.contains(cose)) ||
                        (jose != null && availableJoseSet.contains(jose))
            }
        }

        private fun extractDisplay(displayJson: JsonElement?): List<Openid4VciIssuerDisplay> {
            if (displayJson == null) {
                return listOf()
            }
            return displayJson.jsonArray.map {
                val displayObj = it.jsonObject
                Openid4VciIssuerDisplay(
                    text = displayObj["name"]!!.jsonPrimitive.content,
                    locale = displayObj["locale"]?.jsonPrimitive?.content ?: "en",
                    logoUrl = displayObj["logo"]?.jsonObject?.get("uri")?.jsonPrimitive?.content
                )
            }
        }

        // Returns null if no compatible configuration could be created
        private fun extractAuthorizationServerMetadata(
            url: String,
            jsonObject: JsonObject
        ): Openid4VciAuthorizationMetadata? {
            val responseType = preferred(
                jsonObject["response_types_supported"]?.jsonArray,
                SUPPORTED_RESPONSE_TYPES
            ) ?: "code" //throw IllegalArgumentException("'response_types_supported' is required in authorization server metadata")
            val codeChallengeMethod = preferred(
                jsonObject["code_challenge_methods_supported"]?.jsonArray,
                SUPPORTED_CODE_CHALLENGE_METHODS
            ) ?: return null
            val dpopSigningAlgorithm = preferredAlgorithm(
                jsonObject["dpop_signing_alg_values_supported"]?.jsonArray,
                SUPPORTED_SIGNATURE_ALGORITHMS,
                Algorithm.ESP256
            ) ?: return null
            val authorizationEndpoint =
                jsonObject["authorization_endpoint"]?.jsonPrimitive?.content
                    ?: "$url/authorize"
            val parEndpoint =
                jsonObject["pushed_authorization_request_endpoint"]?.jsonPrimitive?.content
                    ?: "$url/par"
            val authorizationChallengeEndpoint =
                jsonObject["authorization_challenge_endpoint"]?.jsonPrimitive?.content
            val authMethods =
                jsonObject["token_endpoint_auth_methods_supported"]?.jsonArray
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
            return Openid4VciAuthorizationMetadata(
                baseUrl = url,
                pushedAuthorizationRequestEndpoint = parEndpoint,
                authorizationEndpoint = authorizationEndpoint,
                authorizationChallengeEndpoint = authorizationChallengeEndpoint,
                tokenEndpoint = jsonObject["token_endpoint"]!!.jsonPrimitive.content,
                responseType = responseType,
                codeChallengeMethod = codeChallengeMethod,
                dpopSigningAlgorithm = dpopSigningAlgorithm,
                useClientAssertion = requireClientAssertion
            )
        }

        private fun extractProofType(jsonObject: JsonObject?): Openid4VciProofType? {
            if (jsonObject == null) {
                return Openid4VciNoProof
            }
            val attestation = jsonObject["attestation"]?.jsonObject
            if (attestation != null) {
                val alg = preferredAlgorithm(
                    attestation["proof_signing_alg_values_supported"]?.jsonArray,
                    SUPPORTED_SIGNATURE_ALGORITHMS,
                    Algorithm.ESP256
                )
                if (alg != null) {
                    return Openid4VciProofTypeKeyAttestation(alg)
                }
            }
            val jwt = jsonObject["jwt"]?.jsonObject
            if (jwt != null) {
                val alg = preferredAlgorithm(
                    jwt["proof_signing_alg_values_supported"]?.jsonArray,
                    SUPPORTED_SIGNATURE_ALGORITHMS,
                    Algorithm.ESP256
                )
                if (alg != null) {
                    return Openid4VciProofTypeJwt(alg)
                }
            }
            return null
        }

        private fun extractFormat(jsonObject: JsonObject): Openid4VciFormat? {
            return when (jsonObject["format"]?.jsonPrimitive?.content) {
                "dc+sd-jwt" -> Openid4VciFormatSdJwt(jsonObject["vct"]!!.jsonPrimitive.content)
                "mso_mdoc" -> Openid4VciFormatMdoc(jsonObject["doctype"]!!.jsonPrimitive.content)

                // TODO: out of date, remove
                "vc+sd-jwt" -> Openid4VciFormatSdJwt(jsonObject["vct"]!!.jsonPrimitive.content)

                else -> null
            }
        }

        // Supported methods/algorithms in the order of preference
        // TODO: "JWK" is non-standard, but used by some test servers. Remove it.
        private val SUPPORTED_BINDING_METHODS = listOf("cose_key", "jwk", "JWK")
        private val SUPPORTED_SIGNATURE_ALGORITHMS = listOf(Algorithm.ESP256, Algorithm.ES256)
        private val SUPPORTED_RESPONSE_TYPES = listOf("code")
        private val SUPPORTED_CODE_CHALLENGE_METHODS = listOf("S256")
    }
}

// from .well-known/oauth-authorization-server
internal data class Openid4VciAuthorizationMetadata(
    val baseUrl: String,
    val pushedAuthorizationRequestEndpoint: String?,
    val authorizationEndpoint: String?,
    val authorizationChallengeEndpoint: String?,
    val tokenEndpoint: String,
    val responseType: String,
    val codeChallengeMethod: String,
    val dpopSigningAlgorithm: Algorithm,
    val useClientAssertion: Boolean
)

internal data class Openid4VciIssuerDisplay(
    val text: String,
    val locale: String,
    val logoUrl: String?
)

// Create a configuration object even if it is not fully supported (unsupported fields will have
// null values), so that we can have clear error messages.
internal data class Openid4VciCredentialConfiguration(
    val id: String,
    val scope: String?,
    val cryptographicBindingMethod: String?,
    val credentialSigningAlgorithm: Algorithm?,
    val proofType: Openid4VciProofType?,
    val format: Openid4VciFormat?,
    val display: List<Openid4VciIssuerDisplay>
) {
    val isSupported: Boolean get() = credentialSigningAlgorithm != null &&
            proofType != null && format != null
}

internal sealed class Openid4VciFormat {
    abstract val id: String
}

internal data class Openid4VciFormatMdoc(val docType: String) : Openid4VciFormat() {
    override val id: String get() = "mso_mdoc"
}

internal data class Openid4VciFormatSdJwt(val vct: String) : Openid4VciFormat() {
    override val id: String get() = "dc+sd-jwt"
}

internal sealed class Openid4VciProofType {
    abstract val id: String
}

internal object Openid4VciNoProof : Openid4VciProofType() {
    override val id: String get() = "none"
}

internal data class Openid4VciProofTypeJwt(
    val signingAlgorithm: Algorithm
) : Openid4VciProofType() {
    override val id: String get() = "jwt"
}

internal data class Openid4VciProofTypeKeyAttestation(
    val signingAlgorithm: Algorithm
) : Openid4VciProofType() {
    override val id: String get() = "attestation"
}

internal fun JsonObjectBuilder.putFormat(format: Openid4VciFormat) {
    put("format", JsonPrimitive(format.id))
    when (format) {
        is Openid4VciFormatSdJwt -> {
            put("vct", JsonPrimitive(format.vct))
        }

        is Openid4VciFormatMdoc -> {
            put("doctype", JsonPrimitive(format.docType))
        }
    }
}
