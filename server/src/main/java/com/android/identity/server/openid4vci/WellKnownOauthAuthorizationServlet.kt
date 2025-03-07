package org.multipaz.server.openid4vci

import org.multipaz.flow.server.Configuration
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class WellKnownOauthAuthorizationServlet : BaseServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val baseUrl = this.baseUrl
        resp.writer.write(buildJsonObject {
            put("issuer", JsonPrimitive(baseUrl))
            put("authorization_endpoint", JsonPrimitive("$baseUrl/authorize"))
            // OAuth for First-Party Apps (FiPA)
            put("authorization_challenge_endpoint",
                JsonPrimitive("$baseUrl/authorize-challenge"))
            put("token_endpoint", JsonPrimitive("$baseUrl/token"))
            put("pushed_authorization_request_endpoint", JsonPrimitive("$baseUrl/par"))
            put("require_pushed_authorization_requests", JsonPrimitive(true))
            put("token_endpoint_auth_methods_supported",
                buildJsonArray { add(JsonPrimitive("none")) })
            put("response_types_supported",
                buildJsonArray { add(JsonPrimitive("code")) })
            put("code_challenge_methods_supported",
                buildJsonArray { add(JsonPrimitive("S256")) })
            put("dpop_signing_alg_values_supported",
                buildJsonArray { add(JsonPrimitive("ES256")) })
        }.toString())
    }
}