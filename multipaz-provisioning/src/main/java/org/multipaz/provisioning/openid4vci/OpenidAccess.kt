package org.multipaz.provisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Duration.Companion.seconds

@CborSerializable
class OpenidAccess(
    val accessToken: String,
    val accessTokenExpiration: Instant,
    var dpopNonce: String?,
    var cNonce: String?,
    var tokenEndpoint: String,
    var refreshToken: String?,
) {
    companion object {
        suspend fun parseResponse(tokenEndpoint: String, tokenResponse: HttpResponse): OpenidAccess {
            val dpopNonce = tokenResponse.headers["DPoP-Nonce"]
            val tokenString = String(tokenResponse.readBytes())
            val token = Json.parseToJsonElement(tokenString) as JsonObject
            val accessToken = getField(token, "access_token").content
            val cNonce = getField(token, "c_nonce").content
            val duration = getField(token, "expires_in").long
            val accessTokenExpiration = Clock.System.now() + duration.seconds
            val refreshToken = token["refresh_token"]?.jsonPrimitive?.content
            return OpenidAccess(accessToken, accessTokenExpiration, dpopNonce,
                cNonce, tokenEndpoint, refreshToken)
        }

        private fun getField(jsonElement: JsonObject, name: String): JsonPrimitive {
            val jsonValue =  jsonElement[name]
                ?: throw IllegalArgumentException("No $name in token response")
            if (jsonValue !is JsonPrimitive) {
                throw IllegalArgumentException("Field $name is not primitive")
            }
            return jsonValue
        }
    }
}