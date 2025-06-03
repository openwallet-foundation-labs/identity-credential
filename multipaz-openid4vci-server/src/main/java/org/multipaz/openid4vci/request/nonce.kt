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
import org.multipaz.openid4vci.util.authorizeWithDpop
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.random.Random

/**
 * Endpoint to obtain fresh `c_nonce` (challenge for device binding key attestation).
 */
suspend fun nonce(call: ApplicationCall) {
    val dpop = call.request.headers["DPoP"]
        ?: throw InvalidRequestException("DPoP header required")
    val parts = dpop.split('.')
    if (parts.size != 3) {
        throw InvalidRequestException("DPoP invalid")
    }
    val dpopHeader = Json.parseToJsonElement(
        parts[0].fromBase64Url().decodeToString()
    ).jsonObject
    val dpopKey = EcPublicKey.fromJwk(dpopHeader)
    val id = IssuanceState.lookupIssuanceStateId(dpopKey)
    val state = IssuanceState.getIssuanceState(id)
    val existingDPoPNonce = state.dpopNonce?.toByteArray()?.toBase64Url()
    authorizeWithDpop(call.request, state.dpopKey, existingDPoPNonce, null)
    val dpopNonce = Random.nextBytes(15)
    state.dpopNonce = ByteString(dpopNonce)
    val cNonce = Random.nextBytes(15)
    state.redirectUri = null
    state.cNonce = ByteString(cNonce)
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