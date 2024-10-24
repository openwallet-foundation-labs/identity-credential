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
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceRequestSetupCloudSecureArea
import com.android.identity.issuance.evidence.EvidenceRequestWeb
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseGermanEid
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.evidence.EvidenceResponseNotificationPermission
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseSetupCloudSecureArea
import com.android.identity.issuance.evidence.EvidenceResponseWeb
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.net.URLEncoder


@FlowState(
    flowInterface = ProofingFlow::class
)
@CborSerializable
class FunkeProofingState(
    val clientId: String,
    val issuanceClientId: String,
    val documentId: String,
    val proofingInfo: ProofingInfo,
    val applicationCapabilities: WalletApplicationCapabilities,
    val tokenUri:String,
    val useGermanEId: Boolean,
    var access: FunkeAccess? = null,
    var secureAreaIdentifier: String? = null,
    var secureAreaSetupDone: Boolean = false,
    var tosAcknowleged: Boolean = false,
    var notificationPermissonRequested: Boolean = false,
) {
    companion object {
        private const val TAG = "FunkeProofingState"
    }

    @FlowMethod
    fun getEvidenceRequests(env: FlowEnvironment): List<EvidenceRequest> {
        return if (access == null) {
            if (!tosAcknowleged) {
                val message = env.getInterface(Resources::class)!!
                    .getStringResource("funke/tos.html")!!
                listOf(EvidenceRequestMessage(
                    message = message,
                    assets = emptyMap(),
                    acceptButtonText = "Continue",
                    rejectButtonText = "Cancel"
                ))
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
            } else if (useGermanEId) {
                listOf(EvidenceRequestGermanEid(proofingInfo.authorizeUrl, listOf()))
            } else {
                listOf(EvidenceRequestWeb(proofingInfo.authorizeUrl, proofingInfo.landingUrl))
            }
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
            is EvidenceResponseWeb -> obtainTokenUsingCode(env, evidenceResponse.response, null)
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
        obtainTokenUsingCode(env, response.headers["Location"]!!, dpopNonce)
    }

    private suspend fun obtainTokenUsingCode(
        env: FlowEnvironment,
        location: String,
        dpopNonce: String?
    ) {
        val index = location.indexOf("code=")
        if (index < 0) {
            throw IllegalStateException("No code after web authorization")
        }
        val code = location.substring(index + 5)
        this.access = FunkeUtil.obtainToken(
            env = env,
            tokenUrl = tokenUri,
            clientId = clientId,
            issuanceClientId = issuanceClientId,
            authorizationCode = code,
            codeVerifier = proofingInfo.pkceCodeVerifier,
            dpopNonce = dpopNonce
        )
        Logger.i(TAG, "Token request: success")
    }
}