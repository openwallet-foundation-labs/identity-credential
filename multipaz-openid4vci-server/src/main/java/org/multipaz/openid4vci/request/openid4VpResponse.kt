package org.multipaz.openid4vci.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import org.multipaz.cbor.Cbor
import org.multipaz.document.NameSpacedData
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.multipaz.cbor.Tstr
import org.multipaz.models.verifier.Openid4VpVerifierModel
import org.multipaz.openid4vci.util.IssuanceState
import org.multipaz.openid4vci.util.OpaqueIdType
import org.multipaz.openid4vci.util.codeToId
import org.multipaz.openid4vci.util.idToCode
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import kotlin.time.Duration.Companion.minutes

/**
 * Handles presentation-during-issuance OpenId4VP response from the wallet/client.
 */
suspend fun openid4VpResponse(call: ApplicationCall) {
    val parameters = call.receiveParameters()

    val stateCode = parameters["state"]!!
    val id = codeToId(OpaqueIdType.OPENID4VP_STATE, stateCode)
    val state = IssuanceState.getIssuanceState(id)

    val baseUrl = BackendEnvironment.getInterface(Configuration::class)!!.getValue("base_url")!!
        val credMap = state.openid4VpVerifierModel!!.processResponse(
            "$baseUrl/openid4vp-response",
            parameters["response"]!!
        )

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
                    // For demo purposes just transfer these two.
                    "family_name", "given_name" ->
                        data.putEntry("eu.europa.ec.eudi.pid.1",
                            disclosure.key, Cbor.encode(Tstr(disclosure.value.jsonPrimitive.content)))
                }
            }
        }
    }

    state.credentialData = data.build()
    IssuanceState.updateIssuanceState(id, state)

    val presentationCode = idToCode(OpaqueIdType.OPENID4VP_PRESENTATION, id, 5.minutes)
    call.respondText(
        text = buildJsonObject {
                put("presentation_during_issuance_session", presentationCode)
            }.toString(),
        contentType = ContentType.Application.Json
    )
}
