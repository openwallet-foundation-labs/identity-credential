package org.multipaz.issuance.funke

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.device.AssertionDPoPKey
import org.multipaz.device.DeviceAssertionMaker
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.server.Configuration
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.flow.server.Resources
import org.multipaz.issuance.ApplicationSupport
import org.multipaz.issuance.IssuingAuthorityException
import org.multipaz.issuance.ProofingFlow
import org.multipaz.issuance.WalletApplicationCapabilities
import org.multipaz.issuance.WalletServerSettings
import org.multipaz.issuance.evidence.EvidenceRequest
import org.multipaz.issuance.evidence.EvidenceRequestGermanEid
import org.multipaz.issuance.evidence.EvidenceRequestMessage
import org.multipaz.issuance.evidence.EvidenceRequestNotificationPermission
import org.multipaz.issuance.evidence.EvidenceRequestOpenid4Vp
import org.multipaz.issuance.evidence.EvidenceRequestCredentialOffer
import org.multipaz.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import org.multipaz.issuance.evidence.EvidenceRequestQuestionString
import org.multipaz.issuance.evidence.EvidenceRequestSetupCloudSecureArea
import org.multipaz.issuance.evidence.EvidenceRequestWeb
import org.multipaz.issuance.evidence.EvidenceResponse
import org.multipaz.issuance.evidence.EvidenceResponseGermanEid
import org.multipaz.issuance.evidence.EvidenceResponseMessage
import org.multipaz.issuance.evidence.EvidenceResponseNotificationPermission
import org.multipaz.issuance.evidence.EvidenceResponseOpenid4Vp
import org.multipaz.issuance.evidence.EvidenceResponseCredentialOffer
import org.multipaz.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import org.multipaz.issuance.evidence.EvidenceResponseQuestionString
import org.multipaz.issuance.evidence.EvidenceResponseSetupCloudSecureArea
import org.multipaz.issuance.evidence.EvidenceResponseWeb
import org.multipaz.issuance.evidence.Openid4VciCredentialOffer
import org.multipaz.issuance.evidence.Openid4VciCredentialOfferAuthorizationCode
import org.multipaz.issuance.evidence.Openid4VciCredentialOfferPreauthorizedCode
import org.multipaz.issuance.wallet.ApplicationSupportState
import org.multipaz.securearea.PassphraseConstraints
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.htmlEscape
import org.multipaz.util.toBase64Url
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import kotlin.random.Random

@FlowState(
    flowInterface = ProofingFlow::class
)
@CborSerializable
class FunkeProofingState(
    val credentialIssuerUri: String,
    val credentialConfigurationId: String,
    val clientId: String,
    val issuanceClientId: String,
    val documentId: String,
    val applicationCapabilities: WalletApplicationCapabilities,
    var proofingInfo: ProofingInfo? = null,
    var useGermanEId: Boolean = false,
    var access: FunkeAccess? = null,
    var secureAreaIdentifier: String? = null,
    var secureAreaSetupDone: Boolean = false,
    var tosAcknowleged: Boolean = false,
    var notificationPermissonRequested: Boolean = false,
    var openid4VpRequest: String? = null,
    var txCode: String? = null,
    var credentialOffer: Openid4VciCredentialOffer? = null
) {
    companion object {
        private const val TAG = "FunkeProofingState"
    }

    @FlowMethod
    suspend fun getEvidenceRequests(env: FlowEnvironment): List<EvidenceRequest> {
        val metadata = Openid4VciIssuerMetadata.get(env, credentialIssuerUri)
        val configuration = metadata.credentialConfigurations[credentialConfigurationId]!!
        val credentialOffer = this.credentialOffer
        return if (access == null) {
            // Don't have access token yet.
            if (credentialOffer == null) {
                listOf(EvidenceRequestCredentialOffer())
            } else if (!tosAcknowleged) {
                val message = if (useGermanEId) {
                    env.getInterface(Resources::class)!!
                        .getStringResource("funke/tos.html")!!
                } else {
                    val issuingAuthorityName = metadata.display[0].text
                    val documentName = configuration.display[0].text
                    env.getInterface(Resources::class)!!
                        .getStringResource("generic/tos.html")!!
                        .replace("\$ISSUER_NAME", issuingAuthorityName)
                        .replace("\$ID_NAME", documentName)
                }
                listOf(EvidenceRequestMessage(
                    message = message,
                    assets = emptyMap(),
                    acceptButtonText = "Continue",
                    rejectButtonText = "Cancel"
                ))
            } else if (txCode == null &&
                credentialOffer is Openid4VciCredentialOfferPreauthorizedCode &&
                credentialOffer.txCode != null) {
                val message = "<p>" + credentialOffer.txCode!!.description.htmlEscape() + "</p>"
                listOf(EvidenceRequestQuestionString(
                    message = message,
                    assets = mapOf(),
                    defaultValue = "",
                    acceptButtonText = "OK"
                ))
            } else {
                val list = mutableListOf<EvidenceRequest>()
                val authorizationMetadata = selectAuthorizationServer(metadata)
                val proofingInfo = this.proofingInfo
                if (openid4VpRequest != null &&
                    authorizationMetadata.authorizationChallengeEndpoint != null) {
                    val uri = URI(authorizationMetadata.authorizationChallengeEndpoint)
                    val origin = uri.scheme + ":" + uri.authority
                    list.add(EvidenceRequestOpenid4Vp(origin, openid4VpRequest!!))
                }
                if (proofingInfo != null && authorizationMetadata.authorizationEndpoint != null) {
                    val authorizeUrl =
                        "${authorizationMetadata.authorizationEndpoint}?" + FormUrlEncoder {
                            add("client_id", issuanceClientId)
                            add("request_uri", proofingInfo.requestUri!!)
                        }
                    if (authorizationMetadata.useGermanEId) {
                        list.add(EvidenceRequestGermanEid(authorizeUrl, listOf()))
                    } else {
                        list.add(EvidenceRequestWeb(authorizeUrl, proofingInfo.landingUrl))
                    }
                }
                list
            }
        } else if (!notificationPermissonRequested) {
            listOf(EvidenceRequestNotificationPermission(
                permissionNotGrantedMessage = """
                        ## Receive notifications?
                        
                        If there are updates to your document the issuer will send an updated document
                        to your device. If you are interested, we can send a notification to make you aware
                        of when this happens. This requires granting a permission.
                        
                        If you previously denied this permission, attempting to grant it again might not do
                        anything and you may need to go into Settings and manually enable
                        notifications for this application.
                    """.trimIndent(),
                    grantPermissionButtonText = "Grant Permission",
                    continueWithoutPermissionButtonText = "No Thanks",
                    assets = mapOf()
                )
            )
        } else if (configuration.proofType == Openid4VciNoProof) {
            // Keyless credentials, no more questions
            emptyList()
        } else if (secureAreaIdentifier == null) {
            listOf(
                if (applicationCapabilities.androidKeystoreStrongBoxAvailable) {
                    EvidenceRequestQuestionMultipleChoice(
                        message = "Choose Secure Area",
                        assets = emptyMap(),
                        possibleValues = mapOf(
                            "android" to "Android StrongBox Secure Area (Option C)",
                            "cloud" to "Cloud Secure Area (Option C')"
                        ),
                        acceptButtonText = "Continue"
                    )
                } else {
                    EvidenceRequestMessage(
                        message = "Your device does not have Android StrongBox available. Cloud Secure Area will be used (Option C').",
                        acceptButtonText = "Continue",
                        rejectButtonText = "Cancel",
                        assets = emptyMap()
                    )
                }
            )
        } else if (secureAreaIdentifier!!.startsWith("CloudSecureArea?") && !secureAreaSetupDone) {
            listOf(
                EvidenceRequestSetupCloudSecureArea(
                    cloudSecureAreaIdentifier = secureAreaIdentifier!!,
                    passphraseConstraints = PassphraseConstraints.PIN_SIX_DIGITS,
                    message = "## Choose 6-digit PIN\n\nChoose the PIN to use for the document.\n\nThis is asked every time the document is presented so make sure you memorize it and don't share it with anyone else.",
                    verifyMessage = "## Verify PIN\n\nEnter the PIN you chose in the previous screen.",
                    assets = emptyMap()
                )
            )
        } else {
            emptyList()
        }
    }

    @FlowMethod
    suspend fun sendEvidence(env: FlowEnvironment, evidenceResponse: EvidenceResponse) {
        when (evidenceResponse) {
            is EvidenceResponseCredentialOffer -> {
                val credentialOffer = evidenceResponse.credentialOffer
                this.credentialOffer = credentialOffer
                initializeProofing(env)
                if (credentialOffer is Openid4VciCredentialOfferPreauthorizedCode) {
                    if (credentialOffer.txCode == null) {
                        obtainTokenUsingPreauthorizedCode(env)
                    }
                }
            }
            is EvidenceResponseQuestionString -> {
                txCode = evidenceResponse.answer
                obtainTokenUsingPreauthorizedCode(env)
            }
            is EvidenceResponseGermanEid -> {
                val url = evidenceResponse.url
                if (url != null) {
                    processRedirectUrl(env, url)
                }
            }
            is EvidenceResponseWeb -> {
                val index = evidenceResponse.response.indexOf("code=")
                if (index < 0) {
                    throw IllegalStateException("No code after web authorization")
                }
                val authCode = evidenceResponse.response.substring(index + 5)
                obtainTokenUsingCode(env, authCode, null)
            }
            is EvidenceResponseMessage -> {
                if (!evidenceResponse.acknowledged) {
                    throw IssuingAuthorityException("Issuance rejected")
                }
                if (tosAcknowleged) {
                    secureAreaIdentifier = getCloudSecureAreaId(env)
                } else {
                    tosAcknowleged = true
                }
            }
            is EvidenceResponseQuestionMultipleChoice -> {
                secureAreaIdentifier = if (evidenceResponse.answerId == "cloud") {
                    getCloudSecureAreaId(env)
                } else {
                    "AndroidKeystoreSecureArea"
                }
            }
            is EvidenceResponseSetupCloudSecureArea -> {
                secureAreaSetupDone = true
            }
            is EvidenceResponseNotificationPermission -> {
                notificationPermissonRequested = true
            }
            is EvidenceResponseOpenid4Vp -> {
                processOpenid4VpResponse(env, evidenceResponse.response)
            }
            else -> throw IllegalArgumentException("Unexpected evidence type")
        }
    }

    private fun getCloudSecureAreaId(env: FlowEnvironment): String {
        val cloudSecureAreaUrl = URLEncoder.encode(
            WalletServerSettings(env.getInterface(Configuration::class)!!)
                .cloudSecureAreaUrl, "UTF-8"
        )
        return "CloudSecureArea?id=${documentId}&url=$cloudSecureAreaUrl"
    }

    private suspend fun processRedirectUrl(
        env: FlowEnvironment,
        url: String
    ) {
        val httpClient = env.getInterface(HttpClient::class)!!
        val response = httpClient.get(url) {}
        if (response.status != HttpStatusCode.Found && response.status != HttpStatusCode.SeeOther) {
            Logger.e(TAG, "Authentication error: ${response.status}")
            throw IssuingAuthorityException("eID card rejected by the issuer")
        }
        val dpopNonce = response.headers["DPoP-Nonce"]
        if (dpopNonce == null) {
            // Error
            Logger.e(TAG, "No DPoP nonce in authentication response")
            throw IllegalStateException("No DPoP nonce in authentication response")
        }
        val location = response.headers["Location"]!!
        val code = location.substring(location.indexOf("code=") + 5)
        obtainTokenUsingCode(env, code, dpopNonce)
    }

    private suspend fun obtainTokenUsingCode(
        env: FlowEnvironment,
        authCode: String,
        dpopNonce: String?
    ) {
        val metadata = Openid4VciIssuerMetadata.get(env, credentialIssuerUri)
        this.access = FunkeUtil.obtainToken(
            env = env,
            tokenUrl = selectAuthorizationServer(metadata).tokenEndpoint,
            clientId = clientId,
            issuanceClientId = issuanceClientId,
            authorizationCode = authCode,
            codeVerifier = proofingInfo?.pkceCodeVerifier,
            dpopNonce = dpopNonce
        )
        Logger.i(TAG, "Token request: success")
    }

    private suspend fun obtainTokenUsingPreauthorizedCode(env: FlowEnvironment) {
        val metadata = Openid4VciIssuerMetadata.get(env, credentialIssuerUri)
        this.access = FunkeUtil.obtainToken(
            env = env,
            tokenUrl = selectAuthorizationServer(metadata).tokenEndpoint,
            clientId = clientId,
            issuanceClientId = issuanceClientId,
            preauthorizedCode = (credentialOffer as Openid4VciCredentialOfferPreauthorizedCode).preauthorizedCode,
            txCode = txCode,
            codeVerifier = proofingInfo?.pkceCodeVerifier,
            dpopNonce = null
        )
        Logger.i(TAG, "Token request: success")
    }

    private suspend fun processOpenid4VpResponse(env: FlowEnvironment, response: String) {
        val body = String(openid4VpRequest!!.split('.')[1].fromBase64Url())
        val url = Json.parseToJsonElement(body).jsonObject["response_uri"]!!.jsonPrimitive.content
        val state = Json.parseToJsonElement(body).jsonObject["state"]!!.jsonPrimitive.content
        val httpClient = env.getInterface(HttpClient::class)!!
        val resp = httpClient.post(url) {
            headers {
                append("Content-Type", "application/x-www-form-urlencoded")
            }
            setBody(FormUrlEncoder {
                add("response", response)
                add("state", state)
            }.toString())
        }
        val parsedResponse = Json.parseToJsonElement(String(resp.readBytes())).jsonObject
        val presentationCode =
            parsedResponse["presentation_during_issuance_session"]!!.jsonPrimitive.content
        val metadata = Openid4VciIssuerMetadata.get(env, credentialIssuerUri)
        val authorizationMetadata = selectAuthorizationServer(metadata)
        val dpop = FunkeUtil.generateDPoP(env, clientId,
            authorizationMetadata.authorizationChallengeEndpoint!!, null, null)
        val challengeRequest = FormUrlEncoder {
            add("auth_session", proofingInfo!!.authSession!!)
            add("presentation_during_issuance_session", presentationCode)
        }.toString()
        val challengeResponse = httpClient.post(
            authorizationMetadata.authorizationChallengeEndpoint) {
            headers {
                append("DPoP", dpop)
                append("Content-Type", "application/x-www-form-urlencoded")
            }
            setBody(challengeRequest)
        }
        if (challengeResponse.status != HttpStatusCode.OK) {
            throw IllegalStateException("failed to authorize")
        }
        val parsedChallengeResponse =
            Json.parseToJsonElement(String(challengeResponse.readBytes())).jsonObject
        val authCode = parsedChallengeResponse["authorization_code"]!!.jsonPrimitive.content
        obtainTokenUsingCode(env, authCode, null)
    }

    private fun selectAuthorizationServer(
        metadata: Openid4VciIssuerMetadata
    ): Openid4VciAuthorizationMetadata {
        val authorizationServer = credentialOffer?.authorizationServer
        return if (authorizationServer == null) {
            metadata.authorizationServerList[0]
        } else {
            metadata.authorizationServerList.first { it ->
                it.baseUrl == authorizationServer
            }
        }
    }

    private suspend fun initializeProofing(env: FlowEnvironment) {
        performPushedAuthorizationRequest(env)
        val proofingInfo = this.proofingInfo
        if (proofingInfo?.authSession != null && proofingInfo.openid4VpPresentation != null) {
            val httpClient = env.getInterface(HttpClient::class)!!
            val presentationResponse = httpClient.get(proofingInfo.openid4VpPresentation) {}
            if (presentationResponse.status == HttpStatusCode.OK) {
                openid4VpRequest = String(presentationResponse.readBytes())
            }
        }
        val metadata = Openid4VciIssuerMetadata.get(env, credentialIssuerUri)
        useGermanEId = selectAuthorizationServer(metadata).useGermanEId
    }

    private suspend fun performPushedAuthorizationRequest(env: FlowEnvironment) {
        if (credentialOffer is Openid4VciCredentialOfferPreauthorizedCode) {
            return
        }
        val metadata = Openid4VciIssuerMetadata.get(env, credentialIssuerUri)
        val config = metadata.credentialConfigurations[credentialConfigurationId]!!
        val authorizationMetadata = selectAuthorizationServer(metadata)
        // Use authorization challenge if available, as we want to try it first before falling
        // back to web-based authorization.
        val (endpoint, expectedResponseStatus) =
            if (authorizationMetadata.authorizationChallengeEndpoint != null) {
                Pair(
                    authorizationMetadata.authorizationChallengeEndpoint,
                    HttpStatusCode.BadRequest
                )
            } else if (authorizationMetadata.pushedAuthorizationRequestEndpoint != null) {
                Pair(
                    authorizationMetadata.pushedAuthorizationRequestEndpoint,
                    HttpStatusCode.Created
                )
            } else {
                return
            }
        val pkceCodeVerifier = Random.Default.nextBytes(32).toBase64Url()
        val codeChallenge = Crypto.digest(Algorithm.SHA256, pkceCodeVerifier.toByteArray()).toBase64Url()

        // NB: applicationSupport will only be non-null when running this code locally in the
        // Android Wallet app.
        val applicationSupport = env.getInterface(ApplicationSupport::class)
        val parRedirectUrl: String
        val landingUrl: String
        if (authorizationMetadata.useGermanEId) {
            landingUrl = ""
            // Does not matter, but must be https
            parRedirectUrl = "https://secure.redirect.com"
        } else {
            landingUrl = applicationSupport?.createLandingUrl() ?:
                    ApplicationSupportState(clientId).createLandingUrl(env)
            parRedirectUrl = landingUrl
        }

        val clientKeyInfo = FunkeUtil.communicationKey(env, clientId)
        val clientAssertion = if (applicationSupport != null) {
            // Required when applicationSupport is exposed
            val assertionMaker = env.getInterface(DeviceAssertionMaker::class)!!
            applicationSupport.createJwtClientAssertion(
                clientKeyInfo.attestation,
                assertionMaker.makeDeviceAssertion {
                    AssertionDPoPKey(
                        clientKeyInfo.publicKey,
                        credentialIssuerUri
                    )
                }
            )
        } else {
            ApplicationSupportState(clientId).createJwtClientAssertion(
                env,
                clientKeyInfo.publicKey,
                credentialIssuerUri
            )
        }

        val credentialOffer = this.credentialOffer
        val req = FormUrlEncoder {
            add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-client-attestation")
            if (config.scope != null) {
                add("scope", config.scope)
            }
            if (credentialOffer is Openid4VciCredentialOfferAuthorizationCode) {
                val issuerState = credentialOffer.issuerState
                if (issuerState != null) {
                    add("issuer_state", issuerState)
                }
            }
            add("response_type", "code")
            add("code_challenge_method", "S256")
            add("redirect_uri", parRedirectUrl)
            add("client_assertion", clientAssertion)
            add("code_challenge", codeChallenge)
            add("client_id", issuanceClientId)
        }
        val httpClient = env.getInterface(HttpClient::class)!!
        val response = httpClient.post(endpoint) {
            headers {
                append("Content-Type", "application/x-www-form-urlencoded")
            }
            setBody(req.toString())
        }
        val responseText = String(response.readBytes())
        if (response.status != expectedResponseStatus) {
            Logger.e(TAG, "PAR request error: ${response.status}: $responseText")
            throw IssuingAuthorityException("Error establishing authenticated channel with issuer")
        }
        val parsedResponse = Json.parseToJsonElement(responseText) as JsonObject
        if (response.status == HttpStatusCode.BadRequest) {
            val errorCode = parsedResponse["error"]
            if (errorCode !is JsonPrimitive || errorCode.content != "insufficient_authorization") {
                Logger.e(TAG, "PAR request error: ${response.status}: $responseText")
                throw IssuingAuthorityException("Error establishing authenticated channel with issuer")
            }
        }
        val authSession = parsedResponse["auth_session"]
        val requestUri = parsedResponse["request_uri"]
        val presentation = parsedResponse["presentation"]
        this.proofingInfo = ProofingInfo(
            requestUri = requestUri?.jsonPrimitive?.content,
            authSession = authSession?.jsonPrimitive?.content,
            pkceCodeVerifier = pkceCodeVerifier,
            landingUrl = landingUrl,
            openid4VpPresentation = presentation?.jsonPrimitive?.content
        )
    }
}
