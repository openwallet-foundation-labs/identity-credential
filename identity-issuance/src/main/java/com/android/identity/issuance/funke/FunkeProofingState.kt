package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.Crypto
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceRequestGermanEid
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseGermanEid
import com.android.identity.util.Logger
import com.android.identity.util.toBase64
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import kotlin.random.Random


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
    var token: String? = null
) {
    companion object {
        private const val TAG = "FunkeProofingState"
    }

    @FlowMethod
    fun getEvidenceRequests(env: FlowEnvironment): List<EvidenceRequest> {
        return if (token != null) {
            emptyList()
        } else {
            listOf(EvidenceRequestGermanEid(tcTokenUrl, listOf()))
        }
    }

    @FlowMethod
    suspend fun sendEvidence(env: FlowEnvironment, evidenceResponse: EvidenceResponse) {
        val url = (evidenceResponse as EvidenceResponseGermanEid).url
        token = ""
        if (url == null) {
            // Error
            return
        }
        val httpClient = env.getInterface(HttpClient::class)!!
        val response = httpClient.get(url) {

        }
        val dpopNonce = response.headers["DPoP-Nonce"]
        val location = response.headers["Location"]
        val code = location!!.substring(location.indexOf("code=") + 5)
        if (dpopNonce == null) {
            // Error
            return
        }
        val tokenUrl = "${FunkeUtil.BASE_URL}/c/token"
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
            throw IllegalStateException("Token request error")
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