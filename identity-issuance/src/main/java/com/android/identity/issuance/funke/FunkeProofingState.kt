package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Resources
import com.android.identity.issuance.IssuingAuthorityException
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.WalletApplicationCapabilities
import com.android.identity.issuance.WalletServerSettings
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceRequestGermanEid
import com.android.identity.issuance.evidence.EvidenceRequestMessage
import com.android.identity.issuance.evidence.EvidenceRequestNotificationPermission
import com.android.identity.issuance.evidence.EvidenceRequestOpenid4Vp
import com.android.identity.issuance.evidence.EvidenceRequestPreauthorizedCode
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceRequestSetupCloudSecureArea
import com.android.identity.issuance.evidence.EvidenceRequestWeb
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseGermanEid
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.evidence.EvidenceResponseNotificationPermission
import com.android.identity.issuance.evidence.EvidenceResponseOpenid4Vp
import com.android.identity.issuance.evidence.EvidenceResponsePreauthorizedCode
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseSetupCloudSecureArea
import com.android.identity.issuance.evidence.EvidenceResponseWeb
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.util.Logger
import com.android.identity.util.fromBase64Url
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder

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
    val proofingInfo: ProofingInfo?,
    val applicationCapabilities: WalletApplicationCapabilities,
    val tokenUri: String,
    val useGermanEId: Boolean = false,
    var access: FunkeAccess? = null,
    var secureAreaIdentifier: String? = null,
    var secureAreaSetupDone: Boolean = false,
    var tosAcknowleged: Boolean = false,
    var notificationPermissonRequested: Boolean = false,
    var openid4VpRequest: String? = null
) {
    companion object {
        private const val TAG = "FunkeProofingState"
    }

    @FlowMethod
    suspend fun getEvidenceRequests(env: FlowEnvironment): List<EvidenceRequest> {
        val metadata = Openid4VciIssuerMetadata.get(env, credentialIssuerUri)
        val configuration = metadata.credentialConfigurations[credentialConfigurationId]!!
        return if (access == null) {
            if (!tosAcknowleged) {
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
                listOf(
                    EvidenceRequestMessage(
                        message = message,
                        assets = emptyMap(),
                        acceptButtonText = "Continue",
                        rejectButtonText = "Cancel"
                    )
                )
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
                ))
            } else {
                val list = mutableListOf<EvidenceRequest>(EvidenceRequestPreauthorizedCode())
                if (proofingInfo != null && metadata.authorizationServers.isNotEmpty()) {
                    val authorizationMetadata = metadata.authorizationServers[0]
                    val authorizeUrl =
                        "${authorizationMetadata.authorizationEndpoint}?" + FormUrlEncoder {
                            add("client_id", issuanceClientId)
                            add("request_uri", proofingInfo.requestUri!!)
                        }
                    if (authorizationMetadata.useGermanEId) {
                        list.add(EvidenceRequestGermanEid(authorizeUrl, listOf()))
                    } else {
                        if (openid4VpRequest != null) {
                            val uri = URI(authorizationMetadata.authorizationChallengeEndpoint!!)
                            val origin = uri.scheme + ":" + uri.authority
                            list.add(EvidenceRequestOpenid4Vp(origin, openid4VpRequest!!))
                        }
                        list.add(EvidenceRequestWeb(authorizeUrl, proofingInfo.landingUrl))
                    }
                }
                return list
            }
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
            is EvidenceResponseGermanEid -> if (evidenceResponse.url != null) {
                processRedirectUrl(env, evidenceResponse.url)
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
            is EvidenceResponsePreauthorizedCode -> {
                this.access = FunkeUtil.obtainToken(
                    env = env,
                    tokenUrl = tokenUri,
                    clientId = clientId,
                    issuanceClientId = issuanceClientId,
                    preauthorizedCode = evidenceResponse.code,
                    txCode = evidenceResponse.txCode,
                    codeVerifier = proofingInfo?.pkceCodeVerifier,
                    dpopNonce = null
                )
                Logger.i(TAG, "Token request: success")
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
            tokenUrl = metadata.authorizationServers[0].tokenEndpoint,
            clientId = clientId,
            issuanceClientId = issuanceClientId,
            authorizationCode = authCode,
            codeVerifier = proofingInfo?.pkceCodeVerifier,
            dpopNonce = dpopNonce
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
        val authorizationMetadata = metadata.authorizationServers[0]
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
}