package org.multipaz.provisioning.hardcoded

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.provisioning.DocumentCondition
import org.multipaz.provisioning.DocumentConfiguration
import org.multipaz.provisioning.RegistrationResponse
import org.multipaz.provisioning.evidence.EvidenceResponse
import org.multipaz.provisioning.evidence.toCbor
import org.multipaz.provisioning.fromDataItem
import org.multipaz.provisioning.toDataItem
import org.multipaz.provisioning.evidence.fromCbor

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
        val credentialRequests = buildCborArray {
            simpleCredentialRequests.forEach() { cpoRequest ->
                add(RawCbor(cpoRequest.toCbor()))
            }
        }
        val ceMap = buildCborMap {
            collectedEvidence.forEach() { evidence ->
                put(evidence.key, RawCbor(evidence.value.toCbor()))
            }
        }
        val map = buildCborMap {
            put("registrationResponse", registrationResponse.toDataItem())
            put("state", state.ordinal.toLong())
            put("collectedEvidence", ceMap)
            put("credentialRequests", credentialRequests)
            if (documentConfiguration != null) {
                put("documentConfiguration", documentConfiguration!!.toDataItem())
            }
        }
        return map
    }
}
