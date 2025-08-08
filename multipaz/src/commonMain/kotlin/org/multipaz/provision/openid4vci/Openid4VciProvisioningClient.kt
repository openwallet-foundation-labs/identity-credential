package org.multipaz.provision.openid4vci

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.parameters
import io.ktor.http.protocolWithAuthority
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.provision.AuthorizationChallenge
import org.multipaz.provision.AuthorizationResponse
import org.multipaz.provision.KeyBindingInfo
import org.multipaz.provision.CredentialFormat
import org.multipaz.provision.KeyBindingType
import org.multipaz.provision.ProvisioningClient
import org.multipaz.provision.ProvisioningMetadata
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class Openid4VciProvisioningClient(
    val clientPreferences: Openid4VciClientPreferences,
    val credentialOffer: CredentialOffer,
    val issuerConfiguration: IssuerConfiguration,
    val authorizationConfiguration: AuthorizationConfiguration,
): ProvisioningClient {
    var pkceCodeVerifier: String? = null
    var token: String? = null
    var tokenExpiration: Instant? = null
    var refreshToken: String? = null
    var authorizationDPoPNonce: String? = null
    var issuerDPoPNonce: String? = null
    var keyChallenge: String? = null

    override suspend fun getMetadata(): ProvisioningMetadata {
        val fullMetadata = issuerConfiguration.provisioningMetadata
        val credentialId = credentialOffer.configurationId
        return ProvisioningMetadata(
            display = fullMetadata.display,
            credentials = mapOf(credentialId to fullMetadata.credentials[credentialId]!!)
        )
    }

    override suspend fun getAuthorizationChallenges(): List<AuthorizationChallenge> {
        if (token != null) {
            return listOf()
        }
        val requestUri = performPushedAuthorizationRequest()
        return listOf(AuthorizationChallenge.OAuth(
            id = "oauth",
            url = buildString {
                append(authorizationConfiguration.authorizationEndpoint)
                append("?client_id=")
                append(clientPreferences.clientId.encodeURLParameter())
                append("&request_uri=")
                append(requestUri.encodeURLParameter())
            }
        ))
    }

    override suspend fun authorize(response: AuthorizationResponse) {
        when (response) {
            is AuthorizationResponse.OAuth -> processOauthResponse(response.parameterizedRedirectUrl)
        }
    }

    override suspend fun getKeyBindingChallenge(): String {
        val credentialConfiguration =
            issuerConfiguration.provisioningMetadata.credentials[credentialOffer.configurationId]!!
        if (credentialConfiguration.keyProofType == KeyBindingType.Keyless) {
            throw IllegalStateException("getKeyBindingChallenge must not be called for keyless credentials")
        }
        // obtain c_nonce (serves as challenge for the device-bound key)
        val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!
        val nonceResponse = httpClient.post(issuerConfiguration.nonceEndpoint!!) {}
        if (nonceResponse.status != HttpStatusCode.OK) {
            throw IllegalStateException("Error getting a nonce")
        }
        Logger.i(TAG, "Got successful response for nonce request")
        val responseText = nonceResponse.readBytes().decodeToString()
        val cNonce = Json.parseToJsonElement(responseText).jsonObject.string("c_nonce")
        keyChallenge = cNonce
        return cNonce
    }

    override suspend fun obtainCredentials(keyInfo: KeyBindingInfo): List<ByteString> {
        refreshAccessIfNeeded()
        val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!

        // without a nonce we may need to retry
        var retry = true
        var credentialResponse: HttpResponse
        val credentialMetadata =
            issuerConfiguration.provisioningMetadata.credentials[credentialOffer.configurationId]!!
        val keyProofs = buildKeyProofs(keyInfo)
        while (true) {
            val dpop = OpenidUtil.generateDPoP(
                clientId = clientPreferences.clientId,
                requestUrl = issuerConfiguration.credentialEndpoint,
                dpopNonce = issuerDPoPNonce,
                accessToken = token
            )
            credentialResponse = httpClient.post(issuerConfiguration.credentialEndpoint) {
                headers {
                    append("Authorization", "DPoP $token")
                    append("DPoP", dpop)
                    contentType(ContentType.Application.Json)
                }
                setBody(buildJsonObject {
                    put("credential_configuration_id", credentialOffer.configurationId)
                    if (keyProofs != null) {
                        put("proofs", keyProofs)
                    }
                    when (credentialMetadata.format) {
                        is CredentialFormat.Mdoc -> {
                            put("format", "mso_mdoc")
                            put("doctype", credentialMetadata.format.docType)
                        }
                        is CredentialFormat.SdJwt -> {
                            put("format", "dc+sd-jwt")
                            put("vct", credentialMetadata.format.vct)
                        }
                    }
                }.toString())
            }
            if (credentialResponse.headers.contains("DPoP-Nonce")) {
                issuerDPoPNonce = credentialResponse.headers["DPoP-Nonce"]!!
                if (retry) {
                    retry = false // don't retry more than once
                    if (credentialResponse.status != HttpStatusCode.OK) {
                        Logger.e(TAG, "Retry with a fresh DPoP nonce")
                        continue  // retry with the nonce
                    }
                }
            }
            break
        }

        val responseText = credentialResponse.readBytes().decodeToString()
        if (credentialResponse.status != HttpStatusCode.OK) {
            Logger.e(TAG,"Credential request error: ${credentialResponse.status} $responseText")
            throw IllegalStateException(
                "Error getting a credential issued: ${credentialResponse.status} $responseText")
        }
        Logger.i(TAG, "Got successful response for credential request")

        val response = Json.parseToJsonElement(responseText) as JsonObject
        return response["credentials"]!!.jsonArray.map {
            if (it !is JsonObject) {
                throw IllegalStateException("Credential must be represented as json string")
            }
            val text = it.string("credential")
            when (credentialMetadata.format) {
                is CredentialFormat.Mdoc -> ByteString(text.fromBase64Url())
                is CredentialFormat.SdJwt -> text.encodeToByteString()
            }
        }
    }

    private suspend fun buildKeyProofs(keyInfo: KeyBindingInfo): JsonElement? =
        when (keyInfo) {
            KeyBindingInfo.Keyless -> null
            is KeyBindingInfo.OpenidProofOfPossession -> buildJsonObject {
                putJsonArray("jwt") {
                    for (jwt in keyInfo.jwtList) {
                        add(jwt)
                    }
                }
            }
            is KeyBindingInfo.Attestation -> buildJsonObject {
                val backend = BackendEnvironment.getInterface(Openid4VciBackend::class)!!
                val jwtKeyAttestation = backend.createJwtKeyAttestation(
                    keyAttestations = keyInfo.attestations,
                    challenge = keyChallenge!!
                )
                putJsonArray("attestation") {
                    add(jwtKeyAttestation)
                }
            }
        }

    private suspend fun performPushedAuthorizationRequest(): String {
        pkceCodeVerifier = Random.Default.nextBytes(32).toBase64Url()
        val codeChallenge = Crypto.digest(
            Algorithm.SHA256,
            pkceCodeVerifier!!.encodeToByteArray()
        ).toBase64Url()

        val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!

        val redirectUrl = Url(clientPreferences.redirectUrl)
        var dpopNonce: String? = null
        var response: HttpResponse

        while (true) {
            // retry loop for DPoP nonce
            val dpop = OpenidUtil.generateDPoP(
                clientId = clientPreferences.clientId,
                requestUrl = authorizationConfiguration.pushedAuthorizationRequestEndpoint,
                dpopNonce = dpopNonce,
                accessToken = null
            )
            val walletAttestation = if (authorizationConfiguration.useClientAssertion) {
                null
            } else {
                OpenidUtil.createWalletAttestation(
                    clientId = clientPreferences.clientId,
                    endpoint = authorizationConfiguration.pushedAuthorizationRequestEndpoint
                )
            }
            val clientAssertion = if (authorizationConfiguration.useClientAssertion) {
                OpenidUtil.createClientAssertion(authorizationConfiguration.tokenEndpoint)
            } else {
                null
            }
            response = httpClient.submitForm(
                url = authorizationConfiguration.pushedAuthorizationRequestEndpoint,
                formParameters = parameters {
                    val scope = issuerConfiguration
                        .credentialConfigurations[credentialOffer.configurationId]!!.scope
                    if (scope != null) {
                        append("scope", scope)
                    }
                    if (credentialOffer is CredentialOffer.AuthorizationCode) {
                        val issuerState = credentialOffer.issuerState
                        if (issuerState != null) {
                            append("issuer_state", issuerState)
                        }
                    }
                    if (clientAssertion != null) {
                        append("client_assertion", clientAssertion)
                        append(
                            "client_assertion_type",
                            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
                        )
                    }
                    append("response_type", "code")
                    append("code_challenge_method", "S256")
                    val baseRedirectUrl = redirectUrl.protocolWithAuthority + redirectUrl.encodedPath
                    append("redirect_uri", baseRedirectUrl)
                    append("code_challenge", codeChallenge)
                    append("client_id", clientPreferences.clientId)
                    for (parameter in redirectUrl.parameters.names()) {
                        append(parameter, redirectUrl.parameters[parameter]!!)
                    }
                }
            ) {
                headers {
                    append("DPoP", dpop)
                    //append("Content-Type", "application/x-www-form-urlencoded")
                    if (walletAttestation != null) {
                        append("OAuth-Client-Attestation", walletAttestation.attestationJwt)
                        append("OAuth-Client-Attestation-PoP", walletAttestation.attestationPopJwt)
                    }
                }
            }
            if (response.status == HttpStatusCode.Created) {
                break
            }
            val responseText = response.readBytes().decodeToString()
            Logger.e(TAG, "PAR request error: ${response.status}: $responseText")
            if (dpopNonce == null) {
                dpopNonce = response.headers["DPoP-Nonce"]
                if (dpopNonce != null) {
                    continue  // retry the request with the nonce
                }
            }
            throw IllegalStateException("Error establishing authenticated channel with issuer")
        }
        val responseText = response.readBytes().decodeToString()
        val parsedResponse = Json.parseToJsonElement(responseText).jsonObject
        return parsedResponse.string("request_uri")
    }

    private suspend fun processOauthResponse(parameterizedRedirectUrl: String) {
        val code = Url(parameterizedRedirectUrl).parameters["code"]
            ?: throw IllegalStateException("Openid4Vci: no code in authorization response")
        obtainToken(authorizationCode = code, codeVerifier = pkceCodeVerifier!!)
        pkceCodeVerifier = null
    }

    private suspend fun obtainToken(
        refreshToken: String? = null,
        authorizationCode: String? = null,
        preauthorizedCode: String? = null,
        txCode: String? = null,  // pin or other transaction code
        codeVerifier: String? = null
    ) {
        if (refreshToken == null && authorizationCode == null && preauthorizedCode == null) {
            throw IllegalArgumentException("No authorizations provided")
        }
        val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!
        var retried = false
        // When dpop nonce is null, this loop will run twice, first request will return with error,
        // but will provide fresh, dpop nonce and the second request will get fresh access data.
        while (true) {
            val dpop = OpenidUtil.generateDPoP(
                clientId = clientPreferences.clientId,
                requestUrl = authorizationConfiguration.tokenEndpoint,
                dpopNonce = authorizationDPoPNonce,
                accessToken = null
            )
            val walletAttestation = if (authorizationConfiguration.useClientAssertion) {
                null
            } else {
                OpenidUtil.createWalletAttestation(
                    clientId = clientPreferences.clientId,
                    endpoint = authorizationConfiguration.pushedAuthorizationRequestEndpoint
                )
            }
            val clientAssertion = if (authorizationConfiguration.useClientAssertion) {
                OpenidUtil.createClientAssertion(authorizationConfiguration.tokenEndpoint)
            } else {
                null
            }

            val response = httpClient.submitForm(
                url = authorizationConfiguration.tokenEndpoint,
                formParameters = parameters {
                    if (refreshToken != null) {
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                    }
                    if (authorizationCode != null) {
                        append("grant_type", "authorization_code")
                        append("code", authorizationCode)
                    } else if (preauthorizedCode != null) {
                        append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
                        append("pre-authorized_code", preauthorizedCode)
                        if (txCode != null) {
                            append("tx_code", txCode)
                        }
                    }
                    if (codeVerifier != null) {
                        append("code_verifier", codeVerifier)
                    }
                    if (clientAssertion != null) {
                        append("client_assertion", clientAssertion)
                        append(
                            "client_assertion_type",
                            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
                        )
                    }
                    append("client_id", clientPreferences.clientId)
                }
            ) {
                headers {
                    append("DPoP", dpop)
                    append("Content-Type", "application/x-www-form-urlencoded")
                    if (walletAttestation != null) {
                        append("OAuth-Client-Attestation", walletAttestation.attestationJwt)
                        append("OAuth-Client-Attestation-PoP", walletAttestation.attestationPopJwt)
                    }
                }
            }
            authorizationDPoPNonce = response.headers["DPoP-Nonce"]
            if (response.status != HttpStatusCode.OK) {
                val errResponseText = response.readBytes().decodeToString()
                if (!retried && authorizationDPoPNonce != null) {
                    retried = true
                    Logger.e(TAG, "DPoP nonce refreshed: $errResponseText")
                    continue
                }
                Logger.e(TAG, "Token request error: ${response.status} $errResponseText")
                throw IllegalStateException(
                    if (authorizationCode != null) {
                        "Authorization code rejected by the issuer"
                    } else {
                        "Refresh token (seed credential) rejected by the issuer"
                    }
                )
            }
            val tokenResponseString = response.readBytes().decodeToString()
            val tokenResponse = Json.parseToJsonElement(tokenResponseString) as JsonObject
            token = tokenResponse.string("access_token")
            val duration = tokenResponse.integer("expires_in")
            tokenExpiration = Clock.System.now() + duration.seconds
            val refreshToken = tokenResponse.stringOrNull("refresh_token")
            if (refreshToken != null) {
                this.refreshToken = refreshToken
            }
            return
        }
    }

    private suspend fun refreshAccessIfNeeded() {
        if (token == null && refreshToken == null) {
            throw IllegalStateException("Not authorized")
        }
        val expiration = tokenExpiration
        if (expiration != null && Clock.System.now() + 30.seconds < expiration) {
            // No need to refresh.
            return
        }
        obtainToken(
            refreshToken = refreshToken
                ?: throw IllegalStateException("refresh token was not issued")
        )
        Logger.i(TAG, "Refreshed access tokens")
    }

    companion object: JsonParsing("Openid4Vci") {
        const val TAG = "Openid4VciProvisioningClient"

        suspend fun createFromOffer(
            offerUri: String,
            clientPreferences: Openid4VciClientPreferences
        ): Openid4VciProvisioningClient {
            val credentialOffer = CredentialOffer.parseCredentialOffer(offerUri)
            val issuerConfig = IssuerConfiguration.get(
                url = credentialOffer.issuerUri,
                clientPreferences = clientPreferences
            )
            val authorizationServerUrl = credentialOffer.authorizationServer
                ?: issuerConfig.authorizationServerUrls.first()
            val authorizationConfiguration = AuthorizationConfiguration.get(
                url = authorizationServerUrl,
                clientPreferences = clientPreferences
            )
            return Openid4VciProvisioningClient(
                clientPreferences = clientPreferences,
                credentialOffer = credentialOffer,
                issuerConfiguration = issuerConfig,
                authorizationConfiguration = authorizationConfiguration
            )
        }
    }
}