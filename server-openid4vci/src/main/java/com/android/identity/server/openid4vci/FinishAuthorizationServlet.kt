package com.android.identity.server.openid4vci

import com.android.identity.flow.server.Storage
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.minutes

/**
 * Finish web-based authorization and hand off the session back to the Wallet App (or
 * Wallet Server).
 */
class FinishAuthorizationServlet : BaseServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val issuerState = req.getParameter("issuer_state")
        if (issuerState == null) {
            println("Error")
            errorResponse(resp, "invalid_request", "missing parameter 'issuer_state'")
            return
        }
        val id = codeToId(OpaqueIdType.ISSUER_STATE, issuerState)
        val storage = environment.getInterface(Storage::class)!!
        runBlocking {
            val state = IssuanceState.fromCbor(storage.get("IssuanceState", "", id)!!.toByteArray())
            val redirectUri = state.redirectUri ?: ""
            if (!redirectUri.startsWith("http://") && !redirectUri.startsWith("https://")) {
                resp.contentType = "text/html"
                resp.writer.write(
                    """
                            <!DOCTYPE html>
                            <html>
                            <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                            </head>
                            <body>
                            <a href="$redirectUri?issuer_state=$issuerState">Continue</a>
                            </body>
                            </html>
                        """.trimIndent()
                )
            } else {
                val redirectUrl =
                    redirectUri + "?code=" + idToCode(OpaqueIdType.REDIRECT, id, 2.minutes)
                resp.sendRedirect(redirectUrl)
            }
        }
    }
}