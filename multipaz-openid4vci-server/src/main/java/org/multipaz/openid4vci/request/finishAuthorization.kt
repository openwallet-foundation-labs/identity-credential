package org.multipaz.openid4vci.request

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.multipaz.openid4vci.util.IssuanceState
import org.multipaz.openid4vci.util.OpaqueIdType
import org.multipaz.openid4vci.util.SystemOfRecordAccess
import org.multipaz.openid4vci.util.codeToId
import org.multipaz.openid4vci.util.getSystemOfRecordUrl
import org.multipaz.server.getBaseUrl
import org.multipaz.openid4vci.util.idToCode
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TAG = "finish_authorization"

/**
 * Finish web-based authorization and hand off the session back to the Wallet App (or
 * Wallet Server).
 */
suspend fun finishAuthorization(call: ApplicationCall) {
    val systemOfRecordUrl = BackendEnvironment.getSystemOfRecordUrl()
    val id = if (systemOfRecordUrl == null) {
        // working without system of record
        val issuerState = call.request.queryParameters["issuer_state"]
            ?: throw InvalidRequestException("missing parameter 'issuer_state'")
        codeToId(OpaqueIdType.ISSUER_STATE, issuerState)
    } else {
        // redirected from the system of record
        val state = call.request.queryParameters["state"]
            ?: throw InvalidRequestException("missing parameter 'state'")
        codeToId(OpaqueIdType.RECORDS_STATE, state)
    }
    val authCode = idToCode(OpaqueIdType.REDIRECT, id, 2.minutes)
    val state = IssuanceState.getIssuanceState(id)
    if (systemOfRecordUrl != null) {
        // NB: System-of-Record access code is sensitive. In our simple implementation
        // we store it in the database as plain text. For better security it could be
        // encrypted using hash of authCode.
        state.systemOfRecordAuthCode = call.request.queryParameters["code"]
            ?: throw InvalidRequestException("missing parameter 'code'")
        IssuanceState.updateIssuanceState(id, state)
    }
    val redirectUri = state.redirectUri ?: throw IllegalStateException("No redirect url")
    val parameterizedUri = buildString {
        append(redirectUri)
        append("?code=")
        append(authCode)
        append("&iss=")
        append(BackendEnvironment.getBaseUrl().encodeURLParameter())
        val clientState = state.clientState
        if (!clientState.isNullOrEmpty()) {
            append("&state=")
            append(clientState.encodeURLParameter())
        }
    }
    if (!redirectUri.startsWith("http://") && !redirectUri.startsWith("https://")) {
        call.respondText(
            text = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>Redirecting...</title>
                    </head>
                    <body>
                    <p>Redirecting to your app...</p>
                    <script>
                        // Automatically trigger the URI redirect
                        window.location.href = "$parameterizedUri";
                    </script>
                    <p>If you are not redirected automatically, <a href="$parameterizedUri">click here</a>.</p>
                    </body>
                    </html>
                """.trimIndent(),
            contentType = ContentType.Text.Html
        )
    } else {
        call.respondRedirect(parameterizedUri)
    }
}
