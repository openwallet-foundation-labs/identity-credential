package org.multipaz.records.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.records.data.Identity
import org.multipaz.records.data.TokenType
import org.multipaz.records.data.idToToken
import kotlin.time.Duration

/**
 * Handles `authorize` POST request from the provisioning server.
 *
 * Creates access token based on authentication (for demo purposes we use the name and the
 * date of birth).
 *
 * This request is for temporary integration with our openid4vci implementation. It will be
 * replaced by oauth-style web-based authentication followed by a redirect.
 */
suspend fun identityAuthorize(call: ApplicationCall) {
    val request = Json.parseToJsonElement(call.receiveText()) as JsonObject
    val identities = Identity.findByNameAndDateOfBirth(
        familyName = (request["family_name"] as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("family_name required"),
        givenName = (request["given_name"] as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("given_name required"),
        dateOfBirth = (request["birth_date"] as? JsonPrimitive)?.content?.let {
            LocalDate.parse(it)
        } ?: throw IllegalArgumentException("birth_date required"),
    )
    val tokens = identities.map {
        buildJsonObject {
            put("access_token", idToToken(TokenType.ACCESS_TOKEN, it.id, Duration.INFINITE))
        }
    }
    call.respondText (
        contentType = ContentType.Application.Json,
        text = JsonArray(tokens).toString()
    )
}