package org.multipaz.openid4vci.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import org.multipaz.openid4vci.util.IssuanceState
import org.multipaz.openid4vci.util.OpaqueIdType
import org.multipaz.openid4vci.util.codeToId
import org.multipaz.openid4vci.util.idToCode
import org.multipaz.rpc.handler.InvalidRequestException
import kotlin.time.Duration.Companion.minutes

/**
 * Finish web-based authorization and hand off the session back to the Wallet App (or
 * Wallet Server).
 */
suspend fun finishAuthorization(call: ApplicationCall) {
    val issuerState = call.request.queryParameters["issuer_state"]
        ?: throw InvalidRequestException("missing parameter 'issuer_state'")
    val id = codeToId(OpaqueIdType.ISSUER_STATE, issuerState)
    val state = IssuanceState.getIssuanceState(id)
    val redirectUri = state.redirectUri ?: throw IllegalStateException("No redirect url")
    if (!redirectUri.startsWith("http://") && !redirectUri.startsWith("https://")) {
        call.respondText(
            text = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    </head>
                    <body>
                    <a href="$redirectUri?issuer_state=$issuerState">Continue</a>
                    </body>
                    </html>
                """.trimIndent(),
            contentType = ContentType.Text.Html
        )
    } else {
        val redirectUrl =
            redirectUri + "?code=" + idToCode(OpaqueIdType.REDIRECT, id, 2.minutes)
        call.respondRedirect(redirectUrl)
    }
}