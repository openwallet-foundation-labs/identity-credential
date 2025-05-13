package org.multipaz.server.openid4vci

import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.rpc.handler.InvalidRequestException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.models.verifier.Openid4VpVerifierModel
import java.net.URI
import kotlin.time.Duration.Companion.minutes

/**
 * Generates request for Digital Credential for the browser-based authorization workflow.
 */
class CredentialRequestServlet : BaseServlet() {
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        // TODO: unify with AuthorizeServlet.getOpenid4Vp
        val requestLength = req.contentLength
        val requestData = req.inputStream.readNBytes(requestLength)
        val params = Json.parseToJsonElement(String(requestData)) as JsonObject
        val code = params["code"]?.jsonPrimitive?.content
            ?: throw InvalidRequestException("missing parameter 'code'")
        val id = codeToId(OpaqueIdType.PID_READING, code)
        val stateRef = idToCode(OpaqueIdType.OPENID4VP_STATE, id, 5.minutes)
        blocking {
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
            resp.contentType = "application/oauth-authz-req+jwt"
            resp.writer.write(credentialRequest)
        }
    }
}