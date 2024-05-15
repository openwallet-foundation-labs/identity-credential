package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.handler.FlowEnvironment
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceRequestQuestionString
import com.android.identity.issuance.evidence.EvidenceResponse

/**
 * State of [ProofingFlow] RPC implementation.
 */
@FlowState(flowInterface = ProofingFlow::class)
@CborSerializable
class ProofingState(
    val documentId: String = "",
    var state: String = "first_name",
    val evidence: MutableMap<String, EvidenceResponse> = mutableMapOf()
) {
    companion object

    @FlowMethod
    fun getEvidenceRequests(env: FlowEnvironment): List<EvidenceRequest> {
        return when (state) {
            "first_name" ->
                listOf(EvidenceRequestQuestionString(
                    "What first name should be used for the mDL?",
                    mapOf(),
                    "Boris",
                    "Continue"
                ))
            else -> listOf()
        }
    }

    @FlowMethod
    fun sendEvidence(env: FlowEnvironment, evidenceResponse: EvidenceResponse) {
        evidence[state] = evidenceResponse
        state = "done"
    }
}