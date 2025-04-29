package org.multipaz.provisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.device.AssertionPoPKey
import org.multipaz.device.DeviceAssertionMaker
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.IssuingAuthorityException
import org.multipaz.provisioning.Proofing
import org.multipaz.provisioning.WalletApplicationCapabilities
import org.multipaz.provisioning.ProvisioningBackendSettings
import org.multipaz.provisioning.evidence.EvidenceRequest
import org.multipaz.provisioning.evidence.EvidenceRequestMessage
import org.multipaz.provisioning.evidence.EvidenceRequestNotificationPermission
import org.multipaz.provisioning.evidence.EvidenceRequestOpenid4Vp
import org.multipaz.provisioning.evidence.EvidenceRequestCredentialOffer
import org.multipaz.provisioning.evidence.EvidenceRequestQuestionMultipleChoice
import org.multipaz.provisioning.evidence.EvidenceRequestQuestionString
import org.multipaz.provisioning.evidence.EvidenceRequestSetupCloudSecureArea
import org.multipaz.provisioning.evidence.EvidenceRequestWeb
import org.multipaz.provisioning.evidence.EvidenceResponse
import org.multipaz.provisioning.evidence.EvidenceResponseMessage
import org.multipaz.provisioning.evidence.EvidenceResponseNotificationPermission
import org.multipaz.provisioning.evidence.EvidenceResponseOpenid4Vp
import org.multipaz.provisioning.evidence.EvidenceResponseCredentialOffer
import org.multipaz.provisioning.evidence.EvidenceResponseQuestionMultipleChoice
import org.multipaz.provisioning.evidence.EvidenceResponseQuestionString
import org.multipaz.provisioning.evidence.EvidenceResponseSetupCloudSecureArea
import org.multipaz.provisioning.evidence.EvidenceResponseWeb
import org.multipaz.provisioning.evidence.Openid4VciCredentialOffer
import org.multipaz.provisioning.evidence.Openid4VciCredentialOfferAuthorizationCode
import org.multipaz.provisioning.evidence.Openid4VciCredentialOfferPreauthorizedCode
import org.multipaz.provisioning.wallet.ApplicationSupportState
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
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspector
import java.net.URI
import java.net.URLEncoder
import kotlin.random.Random

@RpcState(endpoint = "openid4vci.proofing")
@CborSerializable
class Openid4VciProofingState(
    val credentialIssuerUri: String,
    val credentialConfigurationId: String,
    val clientId: String,
    val issuanceClientId: String,
    val documentId: String,
    val applicationCapabilities: WalletApplicationCapabilities,
    var proofingInfo: ProofingInfo? = null,
    var access: OpenidAccess? = null,
    var secureAreaIdentifier: String? = null,
    var secureAreaSetupDone: Boolean = false,
    var tosAcknowleged: Boolean = false,
    var notificationPermissonRequested: Boolean = false,
    var openid4VpRequest: String? = null,
    var txCode: String? = null,
    var credentialOffer: Openid4VciCredentialOffer? = null
): Proofing, RpcAuthInspector by RpcAuthBackendDelegate {
    companion object {
        private const val TAG = "Openid4VciProofingState"
    }

    override suspend fun getEvidenceRequests(): List<EvidenceRequest> {
        checkClientId()
        val metadata = Openid4VciIssuerMetadata.get(credentialIssuerUri)
        val configuration = metadata.credentialConfigurations[credentialConfigurationId]!!
        val credentialOffer = this.credentialOffer
        return if (access == null) {
            // Don't have access token yet.
            if (credentialOffer == null) {
                listOf(EvidenceRequestCredentialOffer())
            } else if (!tosAcknowleged) {
                val issuingAuthorityName = metadata.display[0].text
                val documentName = configuration.display[0].text
                val message = BackendEnvironment.getInterface(Resources::class)!!
                    .getStringResource("generic/tos.html")!!
                    .replace("\$ISSUER_NAME", issuingAuthorityName)
                    .replace("\$ID_NAME", documentName)
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
                    list.add(EvidenceRequestWeb(authorizeUrl, proofingInfo.landingUrl))
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

    override suspend fun sendEvidence(evidenceResponse: EvidenceResponse) {
        checkClientId()
        when (evidenceResponse) {
            is EvidenceResponseCredentialOffer -> {
                val credentialOffer = evidenceResponse.credentialOffer
                this.credentialOffer = credentialOffer
                initializeProofing()
                if (credentialOffer is Openid4VciCredentialOfferPreauthorizedCode) {
                    if (credentialOffer.txCode == null) {
                        obtainTokenUsingPreauthorizedCode()
                    }
                }
            }
            is EvidenceResponseQuestionString -> {
                txCode = evidenceResponse.answer
                obtainTokenUsingPreauthorizedCode()
            }
            is EvidenceResponseWeb -> {
                val index = evidenceResponse.response.indexOf("code=")
                if (index < 0) {
                    throw IllegalStateException("No code after web authorization")
                }
                val authCode = evidenceResponse.response.substring(index + 5)
                obtainTokenUsingCode(authCode, null)
            }
            is EvidenceResponseMessage -> {
                if (!evidenceResponse.acknowledged) {
                    throw IssuingAuthorityException("Issuance rejected")
                }
                if (tosAcknowleged) {
                    secureAreaIdentifier = getCloudSecureAreaId()
                } else {
                    tosAcknowleged = true
                }
            }
            is EvidenceResponseQuestionMultipleChoice -> {
                secureAreaIdentifier = if (evidenceResponse.answerId == "cloud") {
                    getCloudSecureAreaId()
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
                processOpenid4VpResponse(evidenceResponse.response)
            }
            else -> throw IllegalArgumentException("Unexpected evidence type")
        }
    }

    private suspend fun getCloudSecureAreaId(): String {
        val cloudSecureAreaUrl = URLEncoder.encode(
            ProvisioningBackendSettings(BackendEnvironment.getInterface(Configuration::class)!!)
                .cloudSecureAreaUrl, "UTF-8"
        )
        return "CloudSecureArea?id=${documentId}&url=$cloudSecureAreaUrl"
    }

    private suspend fun processRedirectUrl(
        env: BackendEnvironment,
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
        obtainTokenUsingCode(code, dpopNonce)
    }

    private suspend fun obtainTokenUsingCode(
        authCode: String,
        dpopNonce: String?
    ) {
        val metadata = Openid4VciIssuerMetadata.get(credentialIssuerUri)
        this.access = OpenidUtil.obtainToken(
            tokenUrl = selectAuthorizationServer(metadata).tokenEndpoint,
            clientId = clientId,
            issuanceClientId = issuanceClientId,
            authorizationCode = authCode,
            codeVerifier = proofingInfo?.pkceCodeVerifier,
            dpopNonce = dpopNonce
        )
        Logger.i(TAG, "Token request: success")
    }

    private suspend fun obtainTokenUsingPreauthorizedCode() {
        val metadata = Openid4VciIssuerMetadata.get(credentialIssuerUri)
        this.access = OpenidUtil.obtainToken(
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

    private suspend fun processOpenid4VpResponse(response: String) {
        val body = String(openid4VpRequest!!.split('.')[1].fromBase64Url())
        val url = Json.parseToJsonElement(body).jsonObject["response_uri"]!!.jsonPrimitive.content
        val state = Json.parseToJsonElement(body).jsonObject["state"]!!.jsonPrimitive.content
        val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!
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
        val metadata = Openid4VciIssuerMetadata.get(credentialIssuerUri)
        val authorizationMetadata = selectAuthorizationServer(metadata)
        val dpop = OpenidUtil.generateDPoP(clientId,
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
        obtainTokenUsingCode(authCode, null)
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

    private suspend fun initializeProofing() {
        performPushedAuthorizationRequest()
        val proofingInfo = this.proofingInfo
        if (proofingInfo?.authSession != null && proofingInfo.openid4VpPresentation != null) {
            val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!
            val presentationResponse = httpClient.get(proofingInfo.openid4VpPresentation) {}
            if (presentationResponse.status == HttpStatusCode.OK) {
                openid4VpRequest = String(presentationResponse.readBytes())
            }
        }
    }

    private suspend fun performPushedAuthorizationRequest() {
        if (credentialOffer is Openid4VciCredentialOfferPreauthorizedCode) {
            return
        }
        val metadata = Openid4VciIssuerMetadata.get(credentialIssuerUri)
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
        val applicationSupport = BackendEnvironment.getInterface(ApplicationSupport::class)
        val landingUrl = applicationSupport?.createLandingUrl() ?:
                    ApplicationSupportState(clientId).createLandingUrl()

        val clientKeyInfo = OpenidUtil.communicationKey(clientId)
        val clientAssertion = if (applicationSupport != null) {
            // Required when applicationSupport is exposed
            val assertionMaker = BackendEnvironment.getInterface(DeviceAssertionMaker::class)!!
            applicationSupport.createJwtClientAssertion(
                clientKeyInfo.attestation,
                assertionMaker.makeDeviceAssertion {
                    AssertionPoPKey(
                        clientKeyInfo.publicKey,
                        credentialIssuerUri
                    )
                }
            )
        } else {
            ApplicationSupportState(clientId).createJwtClientAssertion(
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
            add("redirect_uri", landingUrl)
            add("client_assertion", clientAssertion)
            add("code_challenge", codeChallenge)
            add("client_id", issuanceClientId)
        }
        val httpClient = BackendEnvironment.getInterface(HttpClient::class)!!
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

    private suspend fun checkClientId() {
        check(clientId == RpcAuthContext.getClientId())
    }
}
