package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.RawCbor
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.fromDataItem
import com.android.identity.issuance.toDataItem
import kotlinx.datetime.Instant

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
            val state = DocumentCondition.values().firstOrNull {it.ordinal == stateAsInt}
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
