package com.android.identity.server.openid4vci

import com.android.identity.flow.server.Storage
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import kotlin.time.Duration.Companion.minutes

/**
 * Process the request to run web-based authorization. This is typically the second request
 * (after [ParServlet] request) and it is sent from the browser.
 *
 * Specifics of how the web authorization session is run actually do not matter much for the
 * Wallet App and Wallet Server, as long as the session results in redirecting (or resolving)
 * redirect_uri supplied to [ParServlet] on the previous step.
 */
class AuthorizeServlet : BaseServlet() {

    /**
     * Create a simple web page for the user to enter their name.
     *
     * TODO: flesh this out, use web API for PID-based authentication.
     */
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestUri = req.getParameter("request_uri")
        val code = requestUri.substring(requestUri.lastIndexOf(":") + 1)
        val id = codeToId(OpaqueIdType.PAR_CODE, code)
        val stateStr = idToCode(OpaqueIdType.AUTHORIZATION_STATE, id, 20.minutes)
        resp.contentType = "text/html"
        resp.writer.print("""
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            </head>
            <body>
                <h1>Enter your name</h1>
                <form action="authorize" method="post">
                  <label for="f">First name:</label>
                  <input type="text" id="f" name="f"><br/>
                  <label for="l">Last name:</label>
                  <input type="text" id="l" name="l"><br/>
                  <input type="hidden" name="state" value="$stateStr"/>
                  <input type="submit" value="Submit">
                </form>
                </form>
            </body>
            </html>
        """.trimIndent())
    }

    /**
     * Handle first/last name form submission and redirect to [FinishAuthorizationServlet].
     */
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val code = req.getParameter("state")
        val id = codeToId(OpaqueIdType.AUTHORIZATION_STATE, code)
        val storage = environment.getInterface(Storage::class)!!
        runBlocking {
            val state = IssuanceState.fromCbor(storage.get("IssuanceState", "", id)!!.toByteArray())
            state.credentialData = CredentialData(
                firstName = req.getParameter("f"),
                lastName = req.getParameter("l")
            )
            storage.update("IssuanceState", "", id, ByteString(state.toCbor()))
        }
        val issuerState = idToCode(OpaqueIdType.ISSUER_STATE, id, 5.minutes)
        resp.sendRedirect("finish_authorization?issuer_state=$issuerState")
    }
}