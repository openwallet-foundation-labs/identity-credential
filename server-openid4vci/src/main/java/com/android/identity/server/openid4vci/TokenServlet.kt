package com.android.identity.server.openid4vci

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.flow.server.Storage
import com.android.identity.util.toBase64Url
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Takes control over authentication session after web-based user authentication. This is a
 * counterpart of the [ParServlet]. It checks that (1) hash of code_verifier supplied here matches
 * code_challenge supplied to [ParServlet], (2) performs DPoP authorization using the key
 * in client_assertion to [ParServlet]. Once all the checks are done it issues access token that
 * can be used to request a credential and possibly a refresh token that can be used to request
 * more access tokens.
 *
 * TODO: refresh tokens are currently issued, but not yet processed.
 */
class TokenServlet : BaseServlet() {
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        if (req.getParameter("grant_type") != "authorization_code") {
            println("bad grant type")
            errorResponse(resp, "invalid_request", "invalid parameter 'grant_type'")
            return
        }
        val digest = Crypto.digest(
            Algorithm.SHA256, req.getParameter("code_verifier").toByteArray())
        val id = codeToId(OpaqueIdType.REDIRECT, req.getParameter("code"))
        val storage = environment.getInterface(Storage::class)!!
        val state = runBlocking {
            IssuanceState.fromCbor(storage.get("IssuanceState", "", id)!!.toByteArray())
        }
        if (state.codeChallenge != ByteString(digest)) {
            println("bad authorization: '${digest.toBase64Url()}' and '${state.codeChallenge!!.toByteArray().toBase64Url()}'")
            errorResponse(resp, "authorization", "bad code_verifier")
            return
        }
        try {
            authorizeWithDpop(state.dpopKey, req, state.dpopNonce?.toByteArray()?.toBase64Url(), null)
        } catch (err: IllegalArgumentException) {
            println("bad DPoP authorization: $err")
            errorResponse(resp, "authorization", err.message ?: "unknown")
            return
        }
        val dpopNonce = Random.nextBytes(15)
        state.dpopNonce = ByteString(dpopNonce)
        resp.setHeader("DPoP-Nonce", dpopNonce.toBase64Url())
        val cNonce = Random.nextBytes(15)
        state.codeChallenge = null  // challenge met
        state.redirectUri = null
        state.cNonce = ByteString(cNonce)
        runBlocking {
            storage.update("IssuanceState", "", id, ByteString(state.toCbor()))
        }
        val expiresIn = 60.minutes
        val accessToken = idToCode(OpaqueIdType.ACCESS_TOKEN, id, expiresIn)
        val refreshToken = idToCode(OpaqueIdType.REFRESH_TOKEN, id, Duration.INFINITE)
        resp.contentType = "application/json"
        val accessTokenJson = Json.encodeToString(
            TokenResponse.serializer(), TokenResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                cNonce = cNonce.toBase64Url(),
                expiresIn = expiresIn.inWholeSeconds.toInt(),
                cNonceExpiresIn = expiresIn.inWholeSeconds.toInt(),
                tokenType = "DPoP"
            )
        )
        resp.writer.write(accessTokenJson)
    }
}