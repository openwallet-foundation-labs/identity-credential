package com.android.identity.issuance.simple

import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnelType
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnel
import com.android.identity.util.Logger
import java.lang.IllegalStateException

class SimpleIssuingAuthorityProofingFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val credentialId: String,
    private var currentNode: SimpleIssuingAuthorityProofingGraph.Node?,
    private val tunnelDriverFactory: (() -> SimpleIcaoNfcTunnelDriver)? = null
) : ProofingFlow {
    private var pendingTunnelRequest: EvidenceRequestIcaoNfcTunnel? = null
    private var nfcTunnel: SimpleIcaoNfcTunnelDriver? = null

    companion object {
        private const val TAG = "SimpleIssuingAuthorityProofingFlow"
    }

    override suspend fun getEvidenceRequests(): List<EvidenceRequest> {
        val pendingTunnelRequest = this.pendingTunnelRequest
        if (pendingTunnelRequest != null) {
            this.pendingTunnelRequest = null
            return listOf(pendingTunnelRequest)
        }
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
        val evidence = if (evidenceResponse is EvidenceResponseIcaoNfcTunnel) {
            if (nfcTunnel == null) {
                nfcTunnel = tunnelDriverFactory!!()
                val dataGroups = (currentNode as SimpleIssuingAuthorityProofingGraph.IcaoNfcTunnelNode).dataGroups
                nfcTunnel!!.init(dataGroups)
            }
            val tunnel = nfcTunnel!!
            // This is special case
            val nextRequest = tunnel.handleNfcTunnelResponse(evidenceResponse)
            if (nextRequest.requestType == EvidenceRequestIcaoNfcTunnelType.HANDSHAKE) {
                // end if tunnel workflow; do not send to the client, instead save collected
                // evidence and move on to the next node in the evidence collection graph.
                nfcTunnel = null
                tunnel.collectEvidence()
            } else {
                this.pendingTunnelRequest = nextRequest
                return
            }
        } else {
            evidenceResponse
        }
        val currentNode = this.currentNode ?: throw IllegalStateException("Evidence is not expected")
        issuingAuthority.addCollectedEvidence(credentialId, currentNode.nodeId, evidence)
        this.currentNode = currentNode.selectFollowUp(evidence)
    }

}