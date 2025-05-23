package org.multipaz.openid4vci.server

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelinePhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.multipaz.openid4vci.request.authorizeChallenge
import org.multipaz.openid4vci.request.authorizeGet
import org.multipaz.openid4vci.request.authorizePost
import org.multipaz.openid4vci.request.credential
import org.multipaz.openid4vci.request.credentialRequest
import org.multipaz.openid4vci.request.fetchResource
import org.multipaz.openid4vci.request.finishAuthorization
import org.multipaz.openid4vci.request.nonce
import org.multipaz.openid4vci.request.openid4VpResponse
import org.multipaz.openid4vci.request.pushedAuthorizationRequest
import org.multipaz.openid4vci.request.qrCode
import org.multipaz.openid4vci.request.token
import org.multipaz.openid4vci.request.wellKnownOauthAuthorization
import org.multipaz.openid4vci.request.wellKnownOpenidCredentialIssuer
import org.multipaz.openid4vci.util.AUTHZ_REQ
import org.multipaz.server.ServerConfiguration
import org.multipaz.server.ServerEnvironment
import org.multipaz.util.Logger
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter

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
            } catch (err: Throwable) {
                Logger.e(TAG, "Error", err)
                call.respondText(
                    status = HttpStatusCode.InternalServerError,
                    text = err::class.simpleName + ": " + err.message,
                    contentType = ContentType.Text.Plain
                )
            }
        }
    }
    routing {
        get("/") { runRequest { fetchResource(call, "index.html") } }
        get("/{path...}") {
            runRequest { fetchResource(call, call.parameters["path"]!!) }
        }
        get("/authorize") { runRequest { authorizeGet(call) } }
        post("/authorize") { runRequest { authorizePost(call) } }
        post("/authorize_challenge") { runRequest { authorizeChallenge(call) } }
        post("/credential_request") { runRequest { credentialRequest(call) } }
        post("/credential") { runRequest { credential(call) } }
        get("/finish_authorization") { runRequest { finishAuthorization(call) } }
        post("/nonce") { runRequest { nonce(call) } }
        post("/openid4vp_response") {
            runRequest { openid4VpResponse(call) }
        }
        post("/par") { runRequest { pushedAuthorizationRequest(call) } }
        get("/qr") { runRequest { qrCode(call) } }
        post("/token") { runRequest { token(call) } }
        get("/.well-known/openid-credential-issuer") {
            runRequest { wellKnownOpenidCredentialIssuer(call) }
        }
        get("/.well-known/oauth-authorization-server") {
            runRequest { wellKnownOauthAuthorization(call) }
        }
    }
}

// record requests and responses, if configured.

val RESPONSE_COPY_KEY = AttributeKey<String>("RESPONSE_COPY_KEY")

fun Application.traceCalls(configuration: ServerConfiguration) {
    val traceFile = configuration.getValue("serverTraceFile") ?: return
    install(DoubleReceive)
    val traceStream = if (traceFile == "-") {
        OutputStreamWriter(System.out)
    } else {
        FileWriter(traceFile)
    }
    val before = PipelinePhase("before")
    insertPhaseBefore(ApplicationCallPipeline.Call, before)
    intercept(before) {
        val attributes = call.attributes
        val traceResponse = PipelinePhase("traceResponse")
        call.response.pipeline.insertPhaseAfter(ApplicationSendPipeline.Engine, traceResponse)
        call.response.pipeline.intercept(traceResponse) { response ->
            when (response) {
                is OutgoingContent.ByteArrayContent -> {
                    if (response.contentType == ContentType.Application.Json ||
                        response.contentType == AUTHZ_REQ
                    ) {
                        attributes.put(RESPONSE_COPY_KEY,
                            response.bytes().decodeToString())
                    }
                }
                else -> {}
            }
        }
    }
    insertPhaseAfter(ApplicationCallPipeline.Call, before)
    val after = PipelinePhase("after")
    insertPhaseAfter(ApplicationCallPipeline.Call, after)
    intercept(after) {
        val buffer = StringWriter()
        val trace = PrintWriter(buffer)
        trace.println("============================")
        trace.println("${call.request.httpMethod.value} ${call.request.uri}")
        for (name in call.request.headers.names()) {
            trace.println("$name: ${call.request.headers[name]}")
        }
        if (call.request.httpMethod == HttpMethod.Post || call.request.httpMethod == HttpMethod.Put) {
            val contentType = call.request.contentType()
            if (contentType == ContentType.Application.Json ||
                contentType == ContentType.Application.FormUrlEncoded) {
                trace.println()
                trace.println(call.receiveText())
            } else {
                trace.println("*** body not logged ***")
            }
        }

        trace.println("----------------------------")
        val response = call.response
        val status = response.status() ?: HttpStatusCode.OK
        trace.println("${status.value} ${status.description}")
        for (name in response.headers.allValues().names()) {
            for (value in response.headers.values(name)) {
                trace.println("$name: $value")
            }
        }

        if (call.attributes.contains(RESPONSE_COPY_KEY)) {
            val body = call.attributes[RESPONSE_COPY_KEY]
            trace.println()
            if (body.endsWith('\n')) {
                trace.print(body)
            } else {
                trace.println(body)
            }
        } else {
            trace.println("*** body not logged ***")
        }
        trace.flush()
        traceStream.write(buffer.toString())
        traceStream.flush()
    }
}
