package org.multipaz.server.openid4vci

import org.multipaz.document.NameSpacedData
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.rpc.backend.Resources
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Tstr
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.models.verifier.Openid4VpVerifierModel
import java.net.URI
import kotlin.time.Duration.Companion.minutes

/**
 * Initialize authorization workflow of some sort, based on `request_uri` parameter.
 *
 * When `request_uri` starts with `urn:ietf:params:oauth:request_uri:` run web-based authorization.
 * In this case the request is typically sent from the browser. Specifics of how the web
 * authorization session is run actually do not matter much for the Wallet App and Wallet Server,
 * as long as the session results in redirecting (or resolving) `redirect_uri` supplied
 * to [ParServlet] on the previous step.
 */
class AuthorizeServlet : BaseServlet() {
    companion object {
        const val RESOURCE_BASE = "openid4vci"

        const val OAUTH_REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:"
        const val OPENID4VP_REQUEST_URI_PREFIX = "https://rp.example.com/oidc/request/"
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestUri = req.getParameter("request_uri") ?: ""
        if (requestUri.startsWith(OAUTH_REQUEST_URI_PREFIX)) {
            // Create a simple web page for the user to authorize the credential issuance.
            getHtml(requestUri.substring(OAUTH_REQUEST_URI_PREFIX.length), resp)
        } else if (requestUri.startsWith(OPENID4VP_REQUEST_URI_PREFIX)) {
            // Request a presentation using openid4vp
            getOpenid4Vp(requestUri.substring(OPENID4VP_REQUEST_URI_PREFIX.length), resp)
        } else {
            throw InvalidRequestException("Invalid or missing 'request_uri' parameter")
        }
    }

    private fun getHtml(code: String, resp: HttpServletResponse) {
        val resources = environment.getInterface(Resources::class)!!
        val id = codeToId(OpaqueIdType.PAR_CODE, code)
        val authorizationCode = idToCode(OpaqueIdType.AUTHORIZATION_STATE, id, 20.minutes)
        val pidReadingCode = idToCode(OpaqueIdType.PID_READING, id, 20.minutes)
        val authorizeHtml = resources.getStringResource("$RESOURCE_BASE/authorize.html")!!
        resp.contentType = "text/html"
        resp.writer.print(
            authorizeHtml
                .replace("\$authorizationCode", authorizationCode)
                .replace("\$pidReadingCode", pidReadingCode)
        )
    }

    private fun getOpenid4Vp(code: String, resp: HttpServletResponse) {
        val id = codeToId(OpaqueIdType.OPENID4VP_CODE, code)
        val stateRef = idToCode(OpaqueIdType.OPENID4VP_STATE, id, 5.minutes)
        val responseUri = "$baseUrl/openid4vp-response"
        val jwt = blocking {
            val state = IssuanceState.getIssuanceState(id)
            val baseUri = URI(baseUrl)
            val clientId = "x509_san_dns:${baseUri.authority}"
            state.openid4VpVerifierModel = Openid4VpVerifierModel(clientId)
            val jwtRequest = state.openid4VpVerifierModel!!.makeRequest(
                state = stateRef,
                responseUri = responseUri,
                responseMode = "direct_post.jwt",
                requests = mapOf(
                    "pid" to EUPersonalID.getDocumentType().cannedRequests.first { it.id == "full" }
                )
            )
            IssuanceState.updateIssuanceState(id, state)
            jwtRequest
        }
        resp.contentType = "application/oauth-authz-req+jwt"
        resp.writer.write(jwt)
    }

    /**
     * Handle user's authorization and redirect to [FinishAuthorizationServlet].
     */
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val code = req.getParameter("authorizationCode")
        val id = codeToId(OpaqueIdType.AUTHORIZATION_STATE, code)

        val pidData = req.getParameter("pidData")

        val baseUri = URI(this.baseUrl)

        blocking {
            val state = IssuanceState.getIssuanceState(id)
            val origin = baseUri.scheme + "://" + baseUri.authority
            val credMap = state.openid4VpVerifierModel!!.processResponse(
                origin,
                pidData
            )
            state.openid4VpVerifierModel = null

            val presentation = credMap["pid"]!!
            val data = NameSpacedData.Builder()

            when (presentation) {
                is Openid4VpVerifierModel.MdocPresentation -> {
                    for (document in presentation.deviceResponse.documents) {
                        for (namespaceName in document.issuerNamespaces) {
                            for (dataElementName in document.getIssuerEntryNames(namespaceName)) {
                                val value = document.getIssuerEntryData(namespaceName, dataElementName)
                                data.putEntry(namespaceName, dataElementName, value)
                            }
                        }
                    }
                }
                is Openid4VpVerifierModel.SdJwtPresentation -> {
                    for (disclosure in presentation.presentation.sdJwtVc.disclosures) {
                        when (disclosure.key) {
                            "family_name", "given_name" ->
                                data.putEntry("eu.europa.ec.eudi.pid.1",
                                    disclosure.key, Cbor.encode(Tstr(disclosure.value.jsonPrimitive.content)))
                        }
                    }
                }
            }

            state.credentialData = data.build()
            IssuanceState.updateIssuanceState(id, state)
        }

        val issuerState = idToCode(OpaqueIdType.ISSUER_STATE, id, 5.minutes)
        resp.sendRedirect("finish_authorization?issuer_state=$issuerState")
    }
}