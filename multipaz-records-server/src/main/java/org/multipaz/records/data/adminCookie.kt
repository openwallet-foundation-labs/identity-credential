package org.multipaz.records.data

import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.server.getBaseUrl
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** Thrown when administrative access cannot be validated. */
class AdminCookieInvalid: Exception()

/**
 * Ensures the client supplied valid "admin_auth" cookie.
 *
 * Throws [AdminCookieInvalid] exception if no valid cookie is present.
 */
suspend fun validateAdminCookie(call: ApplicationCall) {
    val cookie = call.request.cookies["admin_auth"] ?: throw AdminCookieInvalid()
    try {
        tokenToId(TokenType.ADMIN_COOKIE, cookie)
    } catch (e: IllegalArgumentException) {
        throw AdminCookieInvalid()
    }
}

/**
 * Issues "admin_auth" cookie to the client upon successful authentication
 * (supplied password matching given administrative password).
 */
suspend fun adminAuth(call: ApplicationCall, adminPassword: String) {
    val enteredPassword = call.receiveParameters()["password"]
        ?: throw InvalidRequestException("No password specified")
    if (enteredPassword != adminPassword) {
        call.respondRedirect(BackendEnvironment.getBaseUrl() + "/login.html?err=Wrong+password")
    } else {
        val baseUrl = BackendEnvironment.getBaseUrl()
        val duration = 1.days
        val cookie = idToToken(TokenType.ADMIN_COOKIE, "", duration + 10.seconds)
        call.response.cookies.append(
            name = "admin_auth",
            value = cookie,
            maxAge = duration.inWholeSeconds,
            path = Url(baseUrl).encodedPath + "/"
        )
        call.respondRedirect("$baseUrl/index.html")
    }
}