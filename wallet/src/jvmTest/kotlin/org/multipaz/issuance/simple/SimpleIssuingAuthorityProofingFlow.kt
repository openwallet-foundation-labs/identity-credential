package org.multipaz.issuance.simple

import org.multipaz.cbor.DataItem
import org.multipaz.issuance.evidence.EvidenceRequest
import org.multipaz.issuance.evidence.EvidenceResponse
import org.multipaz.issuance.ProofingFlow
import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import org.multipaz.issuance.evidence.EvidenceResponseIcaoNfcTunnel
import org.multipaz.util.Logger
import java.lang.IllegalStateException

class SimpleIssuingAuthorityProofingFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val documentId: String,
    private var currentNode: SimpleIssuingAuthorityProofingGraph.Node?
) : ProofingFlow {
    private var pendingTunnelRequest: EvidenceRequestIcaoNfcTunnel? = null

    companion object {
        private const val TAG = "SimpleIssuingAuthorityProofingFlow"
    }

    override suspend fun getEvidenceRequests(): List<EvidenceRequest> {
        val pendingTunnelRequest = this.pendingTunnelRequest
        if (pendingTunnelRequest != null) {
            this.pendingTunnelRequest = null
            return listOf(pendingTunnelRequest)
        }
        val currentNode = this.currentNode ?: return emptyList()
        return currentNode.requests
    }

    override suspend fun sendEvidence(evidenceResponse: EvidenceResponse) {
        Logger.d(TAG, "Receiving evidence $evidenceResponse")
        val evidence = if (evidenceResponse is EvidenceResponseIcaoNfcTunnel) {
            throw IllegalArgumentException("NFC tunnel not supported")
        } else {
            evidenceResponse
        }
        val currentNode = this.currentNode ?: throw IllegalStateException("Evidence is not expected")
        issuingAuthority.addCollectedEvidence(documentId, currentNode.nodeId, evidence)
        this.currentNode = currentNode.selectFollowUp(evidence)
    }

    override suspend fun complete() {
        issuingAuthority.setProofingProcessing(documentId)
    }

    // Unused in client implementations
    override val flowPath: String
        get() {
            throw UnsupportedOperationException("Unexpected call")
        }

    // Unused in client implementations
    override val flowState: DataItem
        get() {
            throw UnsupportedOperationException("Unexpected call")
        }
}