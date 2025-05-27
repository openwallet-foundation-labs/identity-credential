package org.multipaz.openid4vci.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration

/**
 * Generates `.well-known/oauth-authorization-server` metadata file.
 */
suspend fun wellKnownOauthAuthorization(call: ApplicationCall) {
    val baseUrl = BackendEnvironment.getInterface(Configuration::class)!!.getValue("base_url")!!
    call.respondText(
        text = buildJsonObject {
            put("issuer", baseUrl)
            put("authorization_endpoint", "$baseUrl/authorize")
            // OAuth for First-Party Apps (FiPA)
            put("authorization_challenge_endpoint", "$baseUrl/authorize_challenge")
            put("token_endpoint", "$baseUrl/token")
            put("pushed_authorization_request_endpoint", "$baseUrl/par")
            put("require_pushed_authorization_requests", true)
            putJsonArray("token_endpoint_auth_methods_supported") {
                add("none")
            }
            putJsonArray("response_types_supported") {
                add("code")
            }
            putJsonArray("code_challenge_methods_supported") {
                add("S256")
            }
            putJsonArray("dpop_signing_alg_values_supported") {
                add("ES256")
            }
        }.toString(),
        contentType = ContentType.Application.Json
    )
}