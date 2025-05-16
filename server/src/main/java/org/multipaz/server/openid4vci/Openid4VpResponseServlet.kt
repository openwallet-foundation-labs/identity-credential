package org.multipaz.server.openid4vci

import org.multipaz.cbor.Cbor
import org.multipaz.document.NameSpacedData
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.multipaz.cbor.Tstr
import org.multipaz.models.verifier.Openid4VpVerifierModel
import kotlin.time.Duration.Companion.minutes

/** Servlet class (may trigger warning as unused in the code). */
class Openid4VpResponseServlet: BaseServlet() {
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val stateCode = req.getParameter("state")!!
        val id = codeToId(OpaqueIdType.OPENID4VP_STATE, stateCode)
        val state = blocking { IssuanceState.getIssuanceState(id) }

        val credMap = state.openid4VpVerifierModel!!.processResponse(
            "$baseUrl/openid4vp-response",
            req.getParameter("response")!!
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
        blocking { IssuanceState.updateIssuanceState(id, state) }

        val presentationCode = idToCode(OpaqueIdType.OPENID4VP_PRESENTATION, id, 5.minutes)
        resp.writer.write(buildJsonObject {
            put("presentation_during_issuance_session", presentationCode)
        }.toString())
    }
}
