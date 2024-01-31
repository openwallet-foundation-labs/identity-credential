package com.android.identity.issuance.simple

import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.ProofingFlow
import com.android.identity.util.Logger
import java.lang.IllegalStateException

class SimpleIssuingAuthorityProofingFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val credentialId: String,
    private val evidenceToCollect: List<EvidenceRequest>,
) : ProofingFlow {

    companion object {
        private const val TAG = "SimpleIssuingAuthorityProofingFlow"
    }

    private var evidenceCursor = 0

    override suspend fun getEvidenceRequests(): List<EvidenceRequest> {
        if (evidenceCursor == evidenceToCollect.size) {
            issuingAuthority.setProofingProcessing(credentialId)
            return emptyList()
        }
        return listOf(evidenceToCollect[evidenceCursor++])
    }

    override suspend fun sendEvidence(evidenceResponse: EvidenceResponse?) {
        Logger.d(TAG, "Receiving evidence $evidenceResponse")
        if (evidenceResponse == null) {
            throw IllegalStateException("Evidence must be supplied")
        }
        issuingAuthority.addCollectedEvidence(credentialId, evidenceResponse)
    }

}