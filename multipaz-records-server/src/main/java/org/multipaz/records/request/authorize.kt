package org.multipaz.records.request

import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import org.multipaz.records.data.AuthorizationData
import org.multipaz.records.data.Identity
import org.multipaz.records.data.OauthParams
import org.multipaz.records.data.fromCbor
import org.multipaz.records.data.toCbor
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.rpc.handler.SimpleCipher
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.time.Duration.Companion.minutes

const val OAUTH_REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:"

private const val TAG = "authorize"

/**
 * Handles `authorize` GET request that comes from the user's browser (being forwarded from
 * openid4vci server) as a second step to access System-of-Record data.
 *
 * After the successful authorization, user is redirected back to openid4vci server (providing
 * authorization code in the process) and subsequently to the user's wallet app.
 */
suspend fun authorizeGet(call: ApplicationCall) {
    val requestUri = call.request.queryParameters["request_uri"] ?: ""
    if (requestUri.startsWith(OAUTH_REQUEST_URI_PREFIX)) {
        // Create a simple web page for the user to authorize the credential issuance.
        getHtml(requestUri.substring(OAUTH_REQUEST_URI_PREFIX.length), call)
    } else {
        throw InvalidRequestException("Invalid or missing 'request_uri' parameter")
    }
}

private suspend fun getHtml(code: String, call: ApplicationCall) {
    val resources = BackendEnvironment.getInterface(Resources::class)!!
    val cipher = BackendEnvironment.getInterface(SimpleCipher::class)!!
    val oauthParams = OauthParams.fromCbor(cipher.decrypt(code.fromBase64Url()))
    val authorizeHtml = resources.getStringResource("authorize.html")!!
    call.respondText(
        text = authorizeHtml
            .replace("\$scope", oauthParams.scope)
            .replace("\$authorizationCode", code),
        contentType = ContentType.Text.Html
    )
}

/**
 * Handles `authorize` POST request from the authorization web page.
 *
 * If the user has successfully authenticated (for demo purposes we use the name and the
 * date of birth), creates access code.
 */
suspend fun authorizePost(call: ApplicationCall) {
    val parameters = call.receiveParameters()
    val code = parameters["authorizationCode"]
        ?: throw InvalidRequestException("'authorizationCode' missing")
    val identities = Identity.findByNameAndDateOfBirth(
        familyName = parameters["family_name"]
            ?: throw IllegalArgumentException("family_name required"),
        givenName = parameters["given_name"]
            ?: throw IllegalArgumentException("given_name required"),
        dateOfBirth = parameters["birth_date"]?.let {
            LocalDate.parse(it)
        } ?: throw IllegalArgumentException("birth_date required"),
    )
    if (identities.isEmpty()) {
        throw IllegalArgumentException("Authorization failed: no such person")
    }
    if (identities.size > 1) {
        throw IllegalArgumentException("Authorization failed: multiple matches")
    }
    val cipher = BackendEnvironment.getInterface(SimpleCipher::class)!!
    val oauthParams = OauthParams.fromCbor(cipher.decrypt(code.fromBase64Url()))
    if (oauthParams.expiration < Clock.System.now()) {
        Logger.e(TAG, "Authorization session expired: ${oauthParams.expiration}")
        throw IllegalArgumentException("Authorization session expired")
    }
    val authCode = cipher.encrypt(AuthorizationData(
        scopeAndId = oauthParams.scope + ":" + identities.first().id,
        codeChallenge = oauthParams.codeChallenge,
        expiration = Clock.System.now() + 3.minutes
    ).toCbor()).toBase64Url()
    call.respondRedirect(buildString {
        append(oauthParams.redirectUri)
        append("?code=")
        append(authCode)
        val clientState = oauthParams.clientState
        if (!clientState.isNullOrEmpty()) {
            append("&state=")
            append(clientState.encodeURLParameter())
        }
    })
}