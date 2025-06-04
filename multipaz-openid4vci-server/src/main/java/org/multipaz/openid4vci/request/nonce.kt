package org.multipaz.openid4vci.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.multipaz.crypto.EcPublicKey
import org.multipaz.openid4vci.util.IssuanceState
import org.multipaz.openid4vci.util.OpaqueIdType
import org.multipaz.openid4vci.util.authorizeWithDpop
import org.multipaz.openid4vci.util.codeToId
import org.multipaz.openid4vci.util.extractAccessToken
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.random.Random

/**
 * Endpoint to obtain fresh `c_nonce` (challenge for device binding key attestation).
 */
suspend fun nonce(call: ApplicationCall) {
    val accessToken = extractAccessToken(call.request)
    val id = codeToId(OpaqueIdType.ACCESS_TOKEN, accessToken)
    val state = IssuanceState.getIssuanceState(id)

    authorizeWithDpop(
        request = call.request,
        publicKey = state.dpopKey ?: throw InvalidRequestException("DPoP was not established"),
        clientId = state.clientId,
        dpopNonce = state.dpopNonce,
        accessToken = accessToken
    )
    val dpopNonce = Random.nextBytes(15)
    state.dpopNonce = ByteString(dpopNonce)
    val cNonce = Random.nextBytes(15)
    state.cNonce = ByteString(cNonce)
    state.redirectUri = null
    state.clientState = null
    IssuanceState.updateIssuanceState(id, state)
    call.response.header("DPoP-Nonce", dpopNonce.toBase64Url())
    call.response.header("Cache-Control", "no-store")

    call.respondText(
        text = buildJsonObject {
            put("c_nonce", cNonce.toBase64Url())
        }.toString(),
        contentType = ContentType.Application.Json
    )
}