package org.multipaz.issuance.hardcoded

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.RawCbor
import org.multipaz.issuance.DocumentCondition
import org.multipaz.issuance.DocumentConfiguration
import org.multipaz.issuance.RegistrationResponse
import org.multipaz.issuance.evidence.EvidenceResponse
import org.multipaz.issuance.evidence.toCbor
import org.multipaz.issuance.fromDataItem
import org.multipaz.issuance.toDataItem
import org.multipaz.issuance.evidence.fromCbor

// The document as seen from the issuer's perspective
data class IssuerDocument(
    val registrationResponse: RegistrationResponse,
    var state: DocumentCondition,
    var collectedEvidence: MutableMap<String, EvidenceResponse>,
    var documentConfiguration: DocumentConfiguration?,
    var simpleCredentialRequests: MutableList<SimpleCredentialRequest>
) {
    companion object {
        fun fromDataItem(map: DataItem): IssuerDocument {
            val registrationResponse = RegistrationResponse.fromDataItem(map["registrationResponse"])

            val stateAsInt = map["state"].asNumber.toInt()
            val state = DocumentCondition.entries.firstOrNull {it.ordinal == stateAsInt}
                ?: throw IllegalArgumentException("Unknown state with value $stateAsInt")

            val collectedEvidence = mutableMapOf<String, EvidenceResponse>()
            val evidenceMap = map["collectedEvidence"].asMap
            for (evidenceId in evidenceMap.keys) {
                collectedEvidence[evidenceId.asTstr] =
                    EvidenceResponse.fromCbor(Cbor.encode(evidenceMap[evidenceId]!!))
            }

            val credentialRequests = mutableListOf<SimpleCredentialRequest>()
            for (credentialRequestDataItem in map["credentialRequests"].asArray) {
                credentialRequests.add(
                    SimpleCredentialRequest.fromCbor(Cbor.encode(credentialRequestDataItem))
                )
            }

            val documentConfiguration: DocumentConfiguration? =
                map.getOrNull("documentConfiguration")?.let {
                    DocumentConfiguration.fromDataItem(it)
                }

            return IssuerDocument(
                registrationResponse,
                state,
                collectedEvidence,
                documentConfiguration,
                credentialRequests,
            )
        }
    }

    fun toDataItem(): DataItem {
        val credentialRequestsBuilder = CborArray.builder()
        simpleCredentialRequests.forEach() { cpoRequest ->
            credentialRequestsBuilder.add(RawCbor(cpoRequest.toCbor()))
        }
        val ceMapBuilder = CborMap.builder()
        collectedEvidence.forEach() { evidence ->
            ceMapBuilder.put(evidence.key, RawCbor(evidence.value.toCbor()))
        }
        val mapBuilder = CborMap.builder()
            .put("registrationResponse", registrationResponse.toDataItem())
            .put("state", state.ordinal.toLong())
            .put("collectedEvidence", ceMapBuilder.end().build())
            .put("credentialRequests", credentialRequestsBuilder.end().build())
        if (documentConfiguration != null) {
            mapBuilder.put("documentConfiguration", documentConfiguration!!.toDataItem())
        }
        return mapBuilder.end().build()
    }
}
