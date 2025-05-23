package org.multipaz.openid4vci.request

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.rpc.handler.InvalidRequestException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.models.verifier.Openid4VpVerifierModel
import org.multipaz.openid4vci.util.AUTHZ_REQ
import org.multipaz.openid4vci.util.IssuanceState
import org.multipaz.openid4vci.util.OpaqueIdType
import org.multipaz.openid4vci.util.codeToId
import org.multipaz.openid4vci.util.idToCode
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import java.net.URI
import kotlin.time.Duration.Companion.minutes

/**
 * Generates request for Digital Credential for the browser-based authorization workflow.
 */
suspend fun credentialRequest(call: ApplicationCall) {
    // TODO: unify with AuthorizeServlet.getOpenid4Vp
    val requestData = call.receiveText()
    val params = Json.parseToJsonElement(requestData) as JsonObject
    val code = params["code"]?.jsonPrimitive?.content
        ?: throw InvalidRequestException("missing parameter 'code'")
    val id = codeToId(OpaqueIdType.PID_READING, code)
    val stateRef = idToCode(OpaqueIdType.OPENID4VP_STATE, id, 5.minutes)
    val baseUrl = BackendEnvironment.getInterface(Configuration::class)!!.getValue("base_url")!!
    val baseUri = URI(baseUrl)
    val origin = baseUri.scheme + "://" + baseUri.authority
    val state = IssuanceState.getIssuanceState(id)
    val model = Openid4VpVerifierModel("x509_san_dns:${baseUri.authority}")
    state.openid4VpVerifierModel = model
    val credentialRequest = model.makeRequest(
        state = stateRef,
        responseMode = "dc_api.jwt",
        expectedOrigins = listOf(origin),
        requests = mapOf(
            "pid" to EUPersonalID.getDocumentType().cannedRequests.first { it.id == "mandatory" }
        )
    )
    IssuanceState.updateIssuanceState(id, state)
    call.respondText(
        text = credentialRequest,
        contentType = AUTHZ_REQ
    )
}