package org.multipaz.backend.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import org.multipaz.provisioning.LandingUrlNotification
import org.multipaz.provisioning.wallet.ApplicationSupportState
import org.multipaz.provisioning.wallet.LandingRecord
import org.multipaz.provisioning.wallet.ProvisioningBackendState
import org.multipaz.provisioning.wallet.emit
import org.multipaz.provisioning.wallet.fromCbor
import org.multipaz.provisioning.wallet.toCbor
import org.multipaz.rpc.backend.Resources
import org.multipaz.rpc.backend.getTable
import org.multipaz.rpc.handler.HttpHandler
import org.multipaz.rpc.handler.RpcDispatcherLocal
import org.multipaz.rpc.handler.RpcExceptionMap
import org.multipaz.rpc.handler.SimpleCipher
import org.multipaz.rpc.transport.HttpTransport
import org.multipaz.util.Logger

const val TAG = "ApplicationExt"

/**
 * Defines server entry points for HTTP GET and POST.
 */
fun Application.configureRouting(configuration: ServerConfiguration) {
    val environment = ServerEnvironment.create(configuration)
    val httpHandler = createHttpHandler(environment)
    routing {
        get ("/") {
            call.respondText("Multipaz RPC server is running")
        }
        get("/landing/{id}") {
            val id = call.parameters["id"]!!
            val env = environment.await()
            val rawUri = call.request.uri
            Logger.i(TAG, "landing $rawUri")
            val queryIndex = rawUri.indexOf("?")
            if (queryIndex < 0) {
                call.respond(HttpStatusCode.BadRequest, "No query")
                return@get
            }
            val storage = env.getTable(ApplicationSupportState.landingTableSpec)
            val recordData = storage.get(id)
            if (recordData == null) {
                call.respond(HttpStatusCode.NotFound, "No such landing id")
                return@get
            }
            val record = LandingRecord.fromCbor(recordData.toByteArray())
            record.resolved = rawUri.substring(queryIndex + 1)
            storage.update(id, ByteString(record.toCbor()))
            val baseUrl = configuration.getValue("base_url")
            val landingUrl = "$baseUrl/${ApplicationSupportState.URL_PREFIX}$id"
            withContext(env) {
                ApplicationSupportState(record.clientId)
                    .emit(LandingUrlNotification(landingUrl))
            }
            val resources = env.getInterface(Resources::class)!!
            val html = resources.getStringResource("landing/index.html")!!
            call.respondText(html, ContentType.Text.Html)
        }
        post("/rpc/{endpoint}/{method}") {
            val endpoint = call.parameters["endpoint"]!!
            val method = call.parameters["method"]!!
            val request = call.receive<ByteArray>()
            val handler = httpHandler.await()
            try {
                val response = handler.post("$endpoint/$method", ByteString(request))
                Logger.i(TAG, "POST $endpoint/$method status 200")
                call.respond(response.toByteArray())
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnsupportedOperationException) {
                Logger.e(TAG, "POST $endpoint/$method status 404", e)
                call.respond(HttpStatusCode.NotFound, e.message ?: "")
            } catch (e: SimpleCipher.DataTamperedException) {
                Logger.e(TAG, "POST $endpoint/$method status 405", e)
                call.respond(HttpStatusCode.MethodNotAllowed, "State tampered")
            } catch (e: IllegalStateException) {
                Logger.e(TAG, "POST $endpoint/$method status 405", e)
                call.respond(HttpStatusCode.MethodNotAllowed, "IllegalStateException")
            } catch (e: HttpTransport.TimeoutException) {
                Logger.e(TAG, "POST $endpoint/$method status 500 (TimeoutException)")
                call.respond(HttpStatusCode.InternalServerError, "TimeoutException")
            } catch (e: Throwable) {
                Logger.e(TAG, "POST $endpoint/$method status 500", e)
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, e.message ?: "")
            }
        }
    }
}

private fun createHttpHandler(
    environment: Deferred<ServerEnvironment>
): Deferred<HttpHandler> {
    return CoroutineScope(Dispatchers.Default).async {
        val env = environment.await()
        val exceptionMapBuilder = RpcExceptionMap.Builder()
        buildExceptionMap(exceptionMapBuilder)
        val dispatcherBuilder = RpcDispatcherLocal.Builder()
        buildDispatcher(dispatcherBuilder)
        val notifications = env.notifications
        val localDispatcher = dispatcherBuilder.build(
            env,
            env.cipher,
            exceptionMapBuilder.build()
        )
        HttpHandler(localDispatcher, notifications)
    }
}

private fun buildExceptionMap(exceptionMapBuilder: RpcExceptionMap.Builder) {
    ProvisioningBackendState.registerExceptions(exceptionMapBuilder)
}

private fun buildDispatcher(dispatcherBuilder: RpcDispatcherLocal.Builder) {
    ProvisioningBackendState.registerAll(dispatcherBuilder)
}
