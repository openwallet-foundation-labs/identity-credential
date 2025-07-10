package org.multipaz.openid4vci.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.buildByteString
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
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

/**
 * Endpoint to obtain fresh `c_nonce` (challenge for device binding key attestation).
 */
suspend fun nonce(call: ApplicationCall) {
    // This is not the most scalable way of managing c_nonce values, but it is the simplest one
    // that would detect c_nonce reuse.
    val cNonce = BackendEnvironment.getTable(credentialChallengeTableSpec).insert(
        key = null,
        data = buildByteString {},
        expiration = Clock.System.now() + 10.minutes
    )
    call.respondText(
        text = buildJsonObject {
            put("c_nonce", cNonce)
        }.toString(),
        contentType = ContentType.Application.Json
    )
}

internal suspend fun validateAndConsumeCredentialChallenge(cNonce: String) {
    val table = BackendEnvironment.getTable(credentialChallengeTableSpec)
    if (!table.delete(cNonce)) {
        throw InvalidRequestException("Expired or invalid c_nonce")
    }
}

private val credentialChallengeTableSpec = StorageTableSpec(
    name = "CredentialChallenge",
    supportExpiration = true,
    supportPartitions = false
)