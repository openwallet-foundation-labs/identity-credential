package org.multipaz.records.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.records.data.IdentityNotFoundException
import org.multipaz.records.request.fetchResource
import org.multipaz.records.request.identityAuthorize
import org.multipaz.records.request.identityDelete
import org.multipaz.records.request.identitySchema
import org.multipaz.records.request.identityGet
import org.multipaz.records.request.identityList
import org.multipaz.records.request.identityPut
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.server.ServerConfiguration
import org.multipaz.server.ServerEnvironment
import org.multipaz.util.Logger

private const val TAG = "ApplicationExt"

private typealias RequestWrapper =
        suspend PipelineContext<*,ApplicationCall>.(
            suspend PipelineContext<*,ApplicationCall>.() -> Unit) -> Unit

/**
 * Defines server endpoints for HTTP GET and POST.
 */
fun Application.configureRouting(configuration: ServerConfiguration) {
    // TODO: when https://youtrack.jetbrains.com/issue/KTOR-8088 is resolved, there
    //  may be a better way to inject our custom wrapper for all request handlers
    //  (instead of doing it for every request like we do today).
    val env = ServerEnvironment.create(configuration)
    val runRequest: RequestWrapper = { body ->
        val self = this
        withContext(env.await()) {
            try {
                body.invoke(self)
            } catch (err: CancellationException) {
                throw err
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
            } catch (err: IdentityNotFoundException) {
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
        get("/") { runRequest { fetchResource(call, "index.html") } }
        get("/{path...}") {
            runRequest { fetchResource(call, call.parameters["path"]!!) }
        }
        post("/identity/list") {
            runRequest { identityList(call) }
        }
        post("/identity/get") {
            runRequest { identityGet(call) }
        }
        post("/identity/create") {
            runRequest { identityPut(call, create = true) }
        }
        post("/identity/update") {
            runRequest { identityPut(call, create = false) }
        }
        post("/identity/delete") {
            runRequest { identityDelete(call) }
        }
        post("/identity/authorize") {
            runRequest { identityAuthorize(call) }
        }
        get("/identity/schema") {
            runRequest { identitySchema(call) }
        }
    }
}
