package com.android.identity.issuance.simple

import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.ProofingFlow
import com.android.identity.util.Logger
import java.lang.IllegalStateException

class SimpleIssuingAuthorityProofingFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val credentialId: String,
    private var currentNode: SimpleIssuingAuthorityProofingGraph.Node?,
) : ProofingFlow {


    companion object {
        private const val TAG = "SimpleIssuingAuthorityProofingFlow"
    }

    override suspend fun getEvidenceRequests(): List<EvidenceRequest> {
        val currentNode = this.currentNode
        if (currentNode == null) {
            issuingAuthority.setProofingProcessing(credentialId)
            return emptyList()
        }
        return currentNode.requests
    }

    override suspend fun sendEvidence(evidenceResponse: EvidenceResponse?) {
        Logger.d(TAG, "Receiving evidence $evidenceResponse")
        if (evidenceResponse == null) {
            throw IllegalStateException("Evidence must be supplied")
        }
        val currentNode = this.currentNode ?: throw IllegalStateException("Evidence is not expected")
        issuingAuthority.addCollectedEvidence(credentialId, currentNode.nodeId, evidenceResponse)
        this.currentNode = currentNode.selectFollowUp(evidenceResponse)
    }

}