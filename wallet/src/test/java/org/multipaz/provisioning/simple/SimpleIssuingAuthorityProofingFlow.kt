package org.multipaz.wallet.provisioning.simple

import org.multipaz.cbor.DataItem
import org.multipaz.provisioning.evidence.EvidenceRequest
import org.multipaz.provisioning.evidence.EvidenceResponse
import org.multipaz.provisioning.Proofing
import org.multipaz.provisioning.evidence.EvidenceRequestIcaoNfcTunnel
import org.multipaz.provisioning.evidence.EvidenceResponseIcaoNfcTunnel
import org.multipaz.util.Logger
import java.lang.IllegalStateException

class SimpleIssuingAuthorityProofingFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    internal val documentId: String,
    private var currentNode: SimpleIssuingAuthorityProofingGraph.Node?
) : Proofing {
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
}