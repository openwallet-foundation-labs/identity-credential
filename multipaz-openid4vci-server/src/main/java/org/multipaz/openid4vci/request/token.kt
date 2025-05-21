package org.multipaz.openid4vci.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.util.toBase64Url
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.openid4vci.util.IssuanceState
import org.multipaz.openid4vci.util.OpaqueIdType
import org.multipaz.openid4vci.util.authorizeWithDpop
import org.multipaz.openid4vci.util.codeToId
import org.multipaz.openid4vci.util.idToCode
import org.multipaz.openid4vci.util.validateClientAttestation
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Takes control over authentication session after web-based user authentication. This is a
 * counterpart of the [pushedAuthorizationRequest]. It checks that (1) hash of code_verifier
 * supplied here matches code_challenge supplied to push authorization, (2) performs DPoP
 * authorization using the key established in push authorization. Once all the checks are done
 * it issues access token that can be used to request a credential and possibly a refresh token
 * that can be used to request more access tokens.
 */
suspend fun token(call: ApplicationCall) {
    val digest: ByteString?
    val parameters = call.receiveParameters()
    val id = when (parameters["grant_type"]) {
        "authorization_code" -> {
            val code = parameters["code"]
                ?: throw InvalidRequestException("'code' parameter missing")
            val codeVerifier = parameters["code_verifier"]
                ?: throw InvalidRequestException("'code_verifier' parameter missing")
            digest = ByteString(Crypto.digest(Algorithm.SHA256, codeVerifier.toByteArray()))
            codeToId(OpaqueIdType.REDIRECT, code)
        }
        "refresh_token" -> {
            val refreshToken = parameters["refresh_token"]
                ?: throw InvalidRequestException("'refresh_token' parameter missing")
            digest = null
            codeToId(OpaqueIdType.REFRESH_TOKEN, refreshToken)
        }
        else -> throw InvalidRequestException("invalid parameter 'grant_type'")
    }
    val state = IssuanceState.getIssuanceState(id)
    if (digest != null) {
        if (state.codeChallenge == digest) {
            state.codeChallenge = null  // challenge met
        } else {
            throw InvalidRequestException("authorization: bad code_verifier")
        }
    }
    validateClientAttestation(call.request, state.clientId)
    authorizeWithDpop(call.request, state.dpopKey, state.dpopNonce?.toByteArray()?.toBase64Url(), null)
    val dpopNonce = Random.nextBytes(15)
    state.dpopNonce = ByteString(dpopNonce)
    val cNonce = Random.nextBytes(15)
    state.redirectUri = null
    state.cNonce = ByteString(cNonce)
    IssuanceState.updateIssuanceState(id, state)
    val expiresIn = 60.minutes
    val accessToken = idToCode(OpaqueIdType.ACCESS_TOKEN, id, expiresIn)
    val refreshToken = idToCode(OpaqueIdType.REFRESH_TOKEN, id, Duration.INFINITE)
    call.response.header("DPoP-Nonce", dpopNonce.toBase64Url())
    call.respondText(
        text = buildJsonObject {
                put("access_token", accessToken)
                put("refresh_token", refreshToken)
                put("expires_in", expiresIn.inWholeSeconds.toInt())
                put("token_type", "DPoP")
            }.toString(),
        contentType = ContentType.Application.Json
    )
}