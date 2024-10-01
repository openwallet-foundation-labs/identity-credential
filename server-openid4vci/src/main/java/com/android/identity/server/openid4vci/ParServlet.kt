package com.android.identity.server.openid4vci

import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.X509Cert
import com.android.identity.flow.server.Storage
import com.android.identity.issuance.common.cache
import com.android.identity.issuance.wallet.ClientAttestationData
import com.android.identity.sdjwt.util.JsonWebKey
import com.android.identity.util.fromBase64Url
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.seconds

/**
 * PAR stands for Pushed Authorization Request, which is the first request to be sent to our
 * OpenID4VCI server. In theory, other, simpler (and less secure) forms of client authorization are
 * possible, but Pushed Authorization Request is required in our implementation.
 *
 * The purpose of this request is for the Wallet App (or Wallet Server serving as a proxy)
 * to establish authorization session on the issuance server. This session is then referenced when
 * performing browser-based proofing/authorization, using requestUri issued in the response from
 * this request. Once web-based authorization is complete, Wallet App re-establishes control over
 * the session in [TokenServlet] by (1) proving that hash of code_verifier matches
 * code_challenge supplied to this service, (2) using DPoP authorization using the key
 * supplied in client_assertion to this service.
 *
 * One of the parameters of this servlet is redirect_uri. When the URL supplied using this
 * parameter is resolved, it signals the end of the web-based user authorization session.
 */
class ParServlet : BaseServlet() {
    companion object {
        const val ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-client-attestation"
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        // Read all parameters
        if (req.getParameter("client_assertion_type") != ASSERTION_TYPE) {
            errorResponse(resp, "invalid_request",
                "invalid parameter 'client_assertion_type'")
            return
        }
        if (req.getParameter("scope") != "pid") {
            errorResponse(resp, "invalid_request", "invalid parameter 'pid'")
            return
        }
        if (req.getParameter("response_type") != "code") {
            errorResponse(resp, "invalid_request", "invalid parameter 'response_type'")
            return
        }
        if (req.getParameter("code_challenge_method") != "S256") {
            errorResponse(resp, "invalid_request", "invalid parameter 'code_challenge_method'")
            return
        }
        val redirectUri = req.getParameter("redirect_uri")
        if (redirectUri == null) {
            errorResponse(resp, "invalid_request", "missing parameter 'redirect_uri'")
            return
        }
        val clientId = req.getParameter("client_id")
        if (clientId == null) {
            errorResponse(resp, "invalid_request", "missing parameter 'client_id'")
            return
        }
        val codeChallenge = try {
            ByteString(req.getParameter("code_challenge").fromBase64Url())
        } catch (err: Exception) {
            errorResponse(resp, "invalid_request", "invalid parameter 'code_challenge'")
            return
        }
        val clientAssertion = req.getParameter("client_assertion")

        // Check that client assertion is signed by the trusted client.
        val sequence = clientAssertion.split("~")
        val clientCertificate = environment.cache(
            ClientCertificate::class,
            clientId
        ) { configuration, resources ->
            // So in real life this should be parameterized by clientId, as different clients will
            // have different public keys.
            val certificateName = configuration.getValue("attestation.certificate")
                ?: "attestation/certificate.pem"
            val certificate = X509Cert.fromPem(resources.getStringResource(certificateName)!!)
            ClientCertificate(certificate)
        }
        checkJwtSignature(clientCertificate.certificate.ecPublicKey, sequence[0])

        // Extract session key (used in DPoP authorization for subsequent requests).
        val parts = sequence[0].split(".")
        if (parts.size != 3) {
            errorResponse(resp, "invalid_assertion", "invalid JWT")
            return
        }
        val assertionBody = Json.parseToJsonElement(String(parts[1].fromBase64Url(), Charsets.UTF_8))
        val dpopKey = JsonWebKey((assertionBody as JsonObject)["cnf"] as JsonObject).asEcPublicKey

        // Create a session
        val storage = environment.getInterface(Storage::class)!!
        val state = IssuanceState(clientId, dpopKey, redirectUri, codeChallenge)
        val id = runBlocking {
            storage.insert("IssuanceState", "", ByteString(state.toCbor()))
        }

        // Format the result (session identifying information).
        val expirationSeconds = 600
        val code = idToCode(OpaqueIdType.PAR_CODE, id, expirationSeconds.seconds)
        resp.status = 201  // Created
        resp.outputStream.write(
            Json.encodeToString(
                ParResponse.serializer(),
                ParResponse(
                    requestUri = "urn:ietf:params:oauth:request_uri:$code",
                    expiresIn = expirationSeconds
                )
            ).toByteArray())
    }
}

data class ClientCertificate(val certificate: X509Cert)