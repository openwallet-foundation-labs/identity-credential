package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceRequestGermanEid
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseGermanEid


@FlowState(
    flowInterface = ProofingFlow::class
)
@CborSerializable
class FunkeProofingState(
    val documentId: String,
    var evidence: EvidenceResponseGermanEid? = null
) {
    companion object

    @FlowMethod
    fun getEvidenceRequests(env: FlowEnvironment): List<EvidenceRequest> {
        return if (evidence != null) {
            emptyList()
        } else {
            listOf(EvidenceRequestGermanEid(listOf()))
        }
    }

    @FlowMethod
    suspend fun sendEvidence(env: FlowEnvironment, evidenceResponse: EvidenceResponse) {
        evidence = evidenceResponse as EvidenceResponseGermanEid
    }
}