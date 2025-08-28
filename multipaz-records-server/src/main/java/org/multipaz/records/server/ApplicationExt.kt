package org.multipaz.records.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.records.data.AdminCookieInvalid
import org.multipaz.records.data.IdentityNotFoundException
import org.multipaz.records.data.adminAuth
import org.multipaz.records.data.validateAdminCookie
import org.multipaz.records.request.authorizeGet
import org.multipaz.records.request.fetchResource
import org.multipaz.records.request.authorizePost
import org.multipaz.records.request.data
import org.multipaz.records.request.identityDelete
import org.multipaz.records.request.identitySchema
import org.multipaz.records.request.identityGet
import org.multipaz.records.request.identityList
import org.multipaz.records.request.identityPut
import org.multipaz.records.request.pushedAuthorizationRequest
import org.multipaz.records.request.token
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.server.ServerConfiguration
import org.multipaz.server.ServerEnvironment
import org.multipaz.server.getBaseUrl
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import kotlin.random.Random

private const val TAG = "ApplicationExt"

private typealias RequestWrapper =
        suspend PipelineContext<*,ApplicationCall>.(
            requireAdminLogin: Boolean,
            body: suspend PipelineContext<*,ApplicationCall>.() -> Unit
        ) -> Unit

/**
 * Defines server endpoints for HTTP GET and POST.
 */
fun Application.configureRouting(configuration: ServerConfiguration) {
    // TODO: when https://youtrack.jetbrains.com/issue/KTOR-8088 is resolved, there
    //  may be a better way to inject our custom wrapper for all request handlers
    //  (instead of doing it for every request like we do today).
    val env = ServerEnvironment.create(configuration)
    val adminPassword = configuration.getValue("admin_password")
        ?: Random.nextBytes(15).toBase64Url().also {
            Logger.e(TAG, "No 'admin_password' in config, generated: '$it'")
        }
    val runRequest: RequestWrapper = { adminOnly, body ->
        val self = this
        withContext(env.await()) {
            try {
                if (adminOnly) {
                    validateAdminCookie(call)
                }
                body.invoke(self)
            } catch (err: CancellationException) {
                throw err
            } catch (_: AdminCookieInvalid) {
                call.respondText(
                    status = HttpStatusCode.Unauthorized,
                    text = "Need to login as admin",
                    contentType = ContentType.Text.Plain
                )
            } catch (err: InvalidRequestException) {
                Logger.e(TAG, "Error", err)
                err.printStackTrace()
                call.respondText(
                    status = HttpStatusCode.BadRequest,
                    text = buildJsonObject {
                        put("error", "invalid")
                        put("error_description", err.message ?: "")
                    }.toString(),
                    contentType = ContentType.Application.Json
                )
            } catch (_: IdentityNotFoundException) {
                call.respondText(
                    status = HttpStatusCode.BadRequest,
                    text = buildJsonObject {
                        put("error", "not_found")
                    }.toString(),
                    contentType = ContentType.Application.Json
                )
            } catch (err: Throwable) {
                Logger.e(TAG, "Error", err)
                err.printStackTrace()
                call.respondText(
                    status = HttpStatusCode.InternalServerError,
                    text = buildJsonObject {
                        put("error", "internal")
                        put("error_description", err::class.simpleName + ": " + err.message)
                    }.toString(),
                    contentType = ContentType.Application.Json
                )
            }
        }
    }
    routing {
        get("/") {
            runRequest(false) {
                val redirect =
                    try {
                        validateAdminCookie(call)
                        "index.html"
                    } catch (err: AdminCookieInvalid) {
                        "login.html"
                    }
                val baseUrl = BackendEnvironment.getBaseUrl()
                call.respondRedirect("$baseUrl/$redirect")
            }
        }
        get("/login.html") {
            runRequest(false) {
                fetchResource(call, "login.html")
            }
        }
        get("/{path...}") {
            val path = call.parameters["path"]!!
            runRequest(false) {
                when (path) {
                    "index.html", "person.html" ->
                        try {
                            validateAdminCookie(call)
                        } catch (_: AdminCookieInvalid) {
                            val baseUrl = BackendEnvironment.getBaseUrl()
                            call.respondRedirect("$baseUrl/login.html")
                            return@runRequest
                        }
                }
                fetchResource(call, path)
            }
        }
        post("/identity/auth") {
            runRequest(false) { adminAuth(call, adminPassword) }
        }
        post("/identity/list") {
            runRequest(true) { identityList(call) }
        }
        post("/identity/get") {
            runRequest(true) { identityGet(call) }
        }
        post("/identity/create") {
            runRequest(true) { identityPut(call, create = true) }
        }
        post("/identity/update") {
            runRequest(true) { identityPut(call, create = false) }
        }
        post("/identity/delete") {
            runRequest(true) { identityDelete(call) }
        }
        get("/identity/schema") {
            runRequest(true) { identitySchema(call) }
        }
        post("/par") {
            runRequest(false) { pushedAuthorizationRequest(call) }
        }
        get("/authorize") {
            runRequest(false) { authorizeGet(call) }
        }
        post("/authorize") {
            runRequest(false) { authorizePost(call) }
        }
        post("/token") {
            runRequest(false) { token(call) }
        }
        get("/data") {
            runRequest(false) { data(call) }
        }
    }
}
