package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.IssuingAuthorityException
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.WalletServerSettings
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceRequestGermanEid
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceRequestSetupCloudSecureArea
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseGermanEid
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseSetupCloudSecureArea
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import java.net.URLEncoder


@FlowState(
    flowInterface = ProofingFlow::class
)
@CborSerializable
class FunkeProofingState(
    val clientId: String,
    val documentId: String,
    val tcTokenUrl: String,
    val pkceCodeVerifier: String,
    var dpopNonce: String? = null,
    var token: String? = null,
    var secureAreaIdentifier: String? = null,
    var secureAreaSetupDone: Boolean = false
) {
    companion object {
        private const val TAG = "FunkeProofingState"
    }

    @FlowMethod
    fun getEvidenceRequests(env: FlowEnvironment): List<EvidenceRequest> {
        return if (token == null) {
            listOf(EvidenceRequestGermanEid(tcTokenUrl, listOf()))
        } else if (secureAreaIdentifier == null) {
            listOf(
                EvidenceRequestQuestionMultipleChoice(
                    message = "Choose Secure Area",
                    assets = emptyMap(),
                    possibleValues = mapOf(
                        "android" to "Android Secure Area (Option C)",
                        "cloud" to "Cloud Secure Area (Option C')"
                    ),
                    acceptButtonText = "Continue"
                )
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
            is EvidenceResponseGermanEid -> processGermanEId(env, evidenceResponse)
            is EvidenceResponseQuestionMultipleChoice -> {
                secureAreaIdentifier = if (evidenceResponse.answerId == "cloud") {
                    val cloudSecureAreaUrl = URLEncoder.encode(
                        WalletServerSettings(env.getInterface(Configuration::class)!!)
                            .cloudSecureAreaUrl, "UTF-8"
                    )
                    "CloudSecureArea?id=${documentId}&url=$cloudSecureAreaUrl"
                } else {
                    "AndroidKeystoreSecureArea"
                }
            }
            is EvidenceResponseSetupCloudSecureArea -> {
                secureAreaSetupDone = true
            }
            else -> throw IllegalArgumentException("Unexpected evidence type")
        }
    }

    private suspend fun processGermanEId(
        env: FlowEnvironment,
        evidenceResponse: EvidenceResponseGermanEid
    ) {
        token = ""
        if (evidenceResponse.url == null) {
            // Error
            return
        }
        val httpClient = env.getInterface(HttpClient::class)!!
        val response = httpClient.get(evidenceResponse.url) {

        }
        val dpopNonce = response.headers["DPoP-Nonce"]
        val location = response.headers["Location"]
        val code = location!!.substring(location.indexOf("code=") + 5)
        if (dpopNonce == null) {
            // Error
            return
        }
        val tokenUrl = "${FunkeUtil.BASE_URL}/token"
        val dpop = FunkeUtil.generateDPoP(env, clientId, tokenUrl, dpopNonce)
        val tokenRequest = FormUrlEncoder {
            add("code", code)
            add("grant_type", "authorization_code")
            add("redirect_uri", "https://secure.redirect.com")  // TODO: It's arbitrary in our case, right?
            add("code_verifier", pkceCodeVerifier)
        }
        val tokenResponse = httpClient.post(tokenUrl) {
            headers {
                append("DPoP", dpop)
                append("Content-Type", "application/x-www-form-urlencoded")
            }
            setBody(tokenRequest.toString())
        }
        if (tokenResponse.status != HttpStatusCode.OK) {
            Logger.e(TAG, "Token request error: ${response.status}")
            throw IssuingAuthorityException("eID card rejected by the issuer")
        }
        this.dpopNonce = tokenResponse.headers["DPoP-Nonce"]
        if (this.dpopNonce == null) {
            Logger.e(TAG, "No DPoP nonce in token response")
            throw IllegalStateException("No DPoP nonce in token response")
        }
        this.token = String(tokenResponse.readBytes())
        Logger.i(TAG, "Token request: got DPoP nonce and a token")
    }
}