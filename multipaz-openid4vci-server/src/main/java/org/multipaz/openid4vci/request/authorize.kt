package org.multipaz.openid4vci.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import kotlinx.datetime.LocalDate
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.Uint
import org.multipaz.cbor.toDataItemFullDate
import org.multipaz.document.NameSpacedData
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.models.verifier.Openid4VpVerifierModel
import org.multipaz.openid4vci.util.AUTHZ_REQ
import org.multipaz.openid4vci.util.IssuanceState
import org.multipaz.openid4vci.util.OAUTH_REQUEST_URI_PREFIX
import org.multipaz.openid4vci.util.OPENID4VP_REQUEST_URI_PREFIX
import org.multipaz.openid4vci.util.OpaqueIdType
import org.multipaz.openid4vci.util.codeToId
import org.multipaz.server.getBaseUrl
import org.multipaz.openid4vci.util.getReaderIdentity
import org.multipaz.openid4vci.util.idToCode
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.Resources
import org.multipaz.rpc.handler.InvalidRequestException
import java.net.URI
import kotlin.time.Duration.Companion.minutes

suspend fun authorizeGet(call: ApplicationCall) {
    val requestUri = call.request.queryParameters["request_uri"] ?: ""
    if (requestUri.startsWith(OAUTH_REQUEST_URI_PREFIX)) {
        // Create a simple web page for the user to authorize the credential issuance.
        getHtml(requestUri.substring(OAUTH_REQUEST_URI_PREFIX.length), call)
    } else if (requestUri.startsWith(OPENID4VP_REQUEST_URI_PREFIX)) {
        // Request a presentation using openid4vp
        getOpenid4Vp(requestUri.substring(OPENID4VP_REQUEST_URI_PREFIX.length), call)
    } else {
        throw InvalidRequestException("Invalid or missing 'request_uri' parameter")
    }
}

private suspend fun getHtml(code: String, call: ApplicationCall) {
    val resources = BackendEnvironment.getInterface(Resources::class)!!
    val id = codeToId(OpaqueIdType.PAR_CODE, code)
    val authorizationCode = idToCode(OpaqueIdType.AUTHORIZATION_STATE, id, 20.minutes)
    val pidReadingCode = idToCode(OpaqueIdType.PID_READING, id, 20.minutes)
    val authorizeHtml = resources.getStringResource("authorize.html")!!
    call.respondText(
        text = authorizeHtml
            .replace("\$authorizationCode", authorizationCode)
            .replace("\$pidReadingCode", pidReadingCode),
        contentType = ContentType.Text.Html
    )
}

private suspend fun getOpenid4Vp(code: String, call: ApplicationCall) {
    val id = codeToId(OpaqueIdType.OPENID4VP_CODE, code)
    val stateRef = idToCode(OpaqueIdType.OPENID4VP_STATE, id, 5.minutes)
    val baseUrl = BackendEnvironment.getBaseUrl()
    val responseUri = "$baseUrl/openid4vp_response"
    val state = IssuanceState.getIssuanceState(id)
    val model = Openid4VpVerifierModel("redirect_uri:$responseUri")
    state.openid4VpVerifierModel = model
    val jwt = state.openid4VpVerifierModel!!.makeRequest(
        state = stateRef,
        responseUri = responseUri,
        responseMode = "direct_post.jwt",
        readerIdentity = getReaderIdentity(),
        requests = mapOf(
            "pid" to EUPersonalID.getDocumentType().cannedRequests.first { it.id == "full" }
        )
    )
    IssuanceState.updateIssuanceState(id, state)
    call.respondText(
        text = jwt,
        contentType = AUTHZ_REQ
    )
}

/**
 * Handle user's authorization and redirect to [FinishAuthorizationServlet].
 */
suspend fun authorizePost(call: ApplicationCall)  {
    val parameters = call.receiveParameters()
    val code = parameters["authorizationCode"]
        ?: throw InvalidRequestException("'authorizationCode' missing")
    val id = codeToId(OpaqueIdType.AUTHORIZATION_STATE, code)
    val data = NameSpacedData.Builder()
    val state = IssuanceState.getIssuanceState(id)

    val baseUrl = BackendEnvironment.getBaseUrl()
    val pidData = parameters["pidData"]
    if (pidData != null) {
        val baseUri = URI(baseUrl)
        val origin = baseUri.scheme + "://" + baseUri.authority
        val credMap = state.openid4VpVerifierModel!!.processResponse(
            origin,
            pidData
        )
        state.openid4VpVerifierModel = null

        when (val presentation = credMap["pid"]!!) {
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
                TODO()
            }
        }
    } else {
        // Since we provision our sample documents off EU PID data, format our collected
        // data using EU PID elements.
        val givenName = parameters["given_name"]
        val familyName = parameters["family_name"]
        val birthDate = parameters["birth_date"]
        if (givenName == null || familyName == null || birthDate == null) {
            throw IllegalArgumentException("Authorization failed")
        }
        data.putEntry(
            nameSpaceName = EUPersonalID.EUPID_NAMESPACE,
            dataElementName = "given_name",
            value = Cbor.encode(Tstr(givenName))
        )
        data.putEntry(
            nameSpaceName = EUPersonalID.EUPID_NAMESPACE,
            dataElementName = "family_name",
            value = Cbor.encode(Tstr(familyName))
        )
        data.putEntry(
            nameSpaceName = EUPersonalID.EUPID_NAMESPACE,
            dataElementName = "birth_date",
            value = Cbor.encode(LocalDate.parse(birthDate).toDataItemFullDate())
        )
        val gender = parameters["gender"]
        if (gender != null) {
            data.putEntry(
                nameSpaceName = EUPersonalID.EUPID_NAMESPACE,
                dataElementName = "sex",
                value = Cbor.encode(Uint(if (gender == "M") 1UL else 2UL))
            )
        }
    }

    state.credentialData = data.build()
    IssuanceState.updateIssuanceState(id, state)

    val issuerState = idToCode(OpaqueIdType.ISSUER_STATE, id, 5.minutes)
    call.respondRedirect(
        "finish_authorization?issuer_state=$issuerState")
}
