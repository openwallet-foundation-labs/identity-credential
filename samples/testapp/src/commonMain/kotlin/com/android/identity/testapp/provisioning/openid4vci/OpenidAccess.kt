package com.android.identity.testapp.provisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    var refreshToken: String?,
    var dpopNonce: String?,
    var tokenEndpoint: String
) {
    companion object {
        suspend fun parseResponse(
            tokenEndpoint: String,
            tokenResponse: HttpResponse
        ): OpenidAccess {
            val dpopNonce = tokenResponse.headers["DPoP-Nonce"]
            val tokenString = tokenResponse.readBytes().decodeToString()
            val token = Json.parseToJsonElement(tokenString) as JsonObject
            val accessToken = getField(token, "access_token").content
            val duration = getField(token, "expires_in").long
            val accessTokenExpiration = Clock.System.now() + duration.seconds
            val refreshToken = token["refresh_token"]?.jsonPrimitive?.content
            return OpenidAccess(accessToken, accessTokenExpiration, refreshToken, dpopNonce,
                tokenEndpoint)
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