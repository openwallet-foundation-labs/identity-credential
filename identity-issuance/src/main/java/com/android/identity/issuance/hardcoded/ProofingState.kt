package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.ProofingFlow
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnel
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.proofing.ProofingGraph
import com.android.identity.issuance.proofing.defaultGraph
import com.android.identity.issuance.tunnel.inProcessMrtdNfcTunnelFactory
import com.android.identity.mrtd.MrtdAccessDataCan

/**
 * State of [ProofingFlow] RPC implementation.
 */
@FlowState(flowInterface = ProofingFlow::class)
@CborSerializable
class ProofingState(
    val documentId: String = "",
    val issuingAuthorityId: String = "",
    val developerModeEnabled: Boolean = false,
    var state: String? = null,
    var done: Boolean = false,
    var nfcTunnelToken: String? = null,
    var pendingTunnelRequest: EvidenceRequest? = null,
    val evidence: MutableMap<String, EvidenceResponse> = mutableMapOf()
) {
    companion object {
        val tunnelProvider = inProcessMrtdNfcTunnelFactory
    }

    @FlowMethod
    fun getEvidenceRequests(env: FlowEnvironment): List<EvidenceRequest> {
        if (done) {
            return listOf()
        }
        if (pendingTunnelRequest != null) {
            val request = pendingTunnelRequest!!
            pendingTunnelRequest = null
            return listOf(request)
        }
        val graph = getGraph(env)
        if (state == null) {
            state = graph.root.nodeId
        }
        return graph.map[state]!!.requests
    }

    @FlowMethod
    suspend fun sendEvidence(env: FlowEnvironment, evidenceResponse: EvidenceResponse) {
        val graph = getGraph(env)
        val node = graph.map[state]
        val newEvidence = if (evidenceResponse is EvidenceResponseIcaoNfcTunnel) {
            // MRTD tunnel is a special case
            val tunnel = if (nfcTunnelToken == null) {
                val dataGroups = (node as ProofingGraph.IcaoNfcTunnelNode).dataGroups
                val mrtdAccessData = if (evidence.containsKey("mrtd_can")) {
                    MrtdAccessDataCan((evidence["mrtd_can"] as EvidenceResponseQuestionString).answer)
                } else {
                    null
                }
                tunnelProvider.acquire(dataGroups, mrtdAccessData)
            } else {
                tunnelProvider.getByToken(nfcTunnelToken!!)
            }
            val nextRequest = tunnel.handleNfcTunnelResponse(evidenceResponse)
            if (nextRequest == null) {
                // end if tunnel workflow; do not send to the client, instead save collected
                // evidence and move on to the next node in the evidence collection graph.
                nfcTunnelToken = null
                tunnel.complete()
            } else {
                nfcTunnelToken = tunnel.token
                pendingTunnelRequest = nextRequest
                return
            }
        } else {
            evidenceResponse
        }

        evidence[state!!] = newEvidence
        val followUp = node!!.selectFollowUp(newEvidence)
        if (followUp != null) {
            state = followUp.nodeId
        } else {
            done = true
        }
    }

    private fun getGraph(env: FlowEnvironment): ProofingGraph {
        val key = GraphKey(issuingAuthorityId, developerModeEnabled)
        return env.cache(ProofingGraph::class, key) { configuration, resources ->
            defaultGraph(resources, developerModeEnabled,
                resources.getStringResource("$issuingAuthorityId/tos.html")!!,
                mapOf("logo.png" to resources.getRawResource("$issuingAuthorityId/logo.png")!!)
            )
        }
    }

    data class GraphKey(val issuingAuthorityId: String, val developerModeEnabled: Boolean)
}