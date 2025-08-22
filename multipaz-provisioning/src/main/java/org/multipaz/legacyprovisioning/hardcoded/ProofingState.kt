package org.multipaz.legacyprovisioning.hardcoded

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.legacyprovisioning.Proofing
import org.multipaz.legacyprovisioning.WalletApplicationCapabilities
import org.multipaz.legacyprovisioning.ProvisioningBackendSettings
import org.multipaz.rpc.cache
import org.multipaz.legacyprovisioning.evidence.EvidenceRequest
import org.multipaz.legacyprovisioning.evidence.EvidenceResponse
import org.multipaz.legacyprovisioning.evidence.EvidenceResponseIcaoNfcTunnel
import org.multipaz.legacyprovisioning.evidence.EvidenceResponseIcaoNfcTunnelResult
import org.multipaz.legacyprovisioning.evidence.EvidenceResponseQuestionString
import org.multipaz.legacyprovisioning.fromCbor
import org.multipaz.legacyprovisioning.proofing.ProofingGraph
import org.multipaz.legacyprovisioning.proofing.defaultGraph
import org.multipaz.legacyprovisioning.tunnel.inProcessMrtdNfcTunnelFactory
import org.multipaz.mrtd.MrtdAccessDataCan
import kotlinx.coroutines.currentCoroutineContext
import org.multipaz.legacyprovisioning.wallet.AuthenticationState
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.storage.Storage

/**
 * State of [Proofing] RPC implementation.
 */
@RpcState(endpoint= "hardcoded.proofing")
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
): Proofing, RpcAuthInspector by RpcAuthBackendDelegate {
    companion object {
        val tunnelProvider = inProcessMrtdNfcTunnelFactory
    }

    override suspend fun getEvidenceRequests(): List<EvidenceRequest> {
        if (done) {
            return listOf()
        }
        if (pendingTunnelRequest != null) {
            val request = pendingTunnelRequest!!
            pendingTunnelRequest = null
            return listOf(request)
        }
        val graph = getGraph()
        if (state == null) {
            state = graph.root.nodeId
        }
        return graph.map[state]!!.requests
    }

    override suspend fun sendEvidence(evidenceResponse: EvidenceResponse) {
        val graph = getGraph()
        val node = graph.map[state]
        // Certain evidence types require special processing
        val newEvidence = when (evidenceResponse) {
            is EvidenceResponseIcaoNfcTunnel -> {
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
            }
            is EvidenceResponseIcaoNfcTunnelResult -> {
                throw IllegalArgumentException("illegal evidence type")
            }
            else -> evidenceResponse
        }

        evidence[state!!] = newEvidence
        val followUp = node!!.selectFollowUp(newEvidence)
        if (followUp != null) {
            state = followUp.nodeId
        } else {
            done = true
        }
    }

    private suspend fun getGraph(): ProofingGraph {
        val clientId = RpcAuthContext.getClientId()
        val env = BackendEnvironment.get(currentCoroutineContext())
        val storage = env.getInterface(Storage::class)!!.getTable(
            AuthenticationState.walletAppCapabilitiesTableSpec)
        val walletApplicationCapabilities = storage.get(clientId)?.let {
            WalletApplicationCapabilities.fromCbor(it.toByteArray())
        } ?: throw IllegalStateException("WalletApplicationCapabilities not found")

        val key = GraphKey(issuingAuthorityId, documentId, developerModeEnabled)
        return env.cache(ProofingGraph::class, key) { configuration, resources ->
            val cloudSecureAreaUrl = ProvisioningBackendSettings(configuration).cloudSecureAreaUrl
            defaultGraph(
                documentId,
                resources,
                walletApplicationCapabilities,
                developerModeEnabled,
                cloudSecureAreaUrl,
                resources.getStringResource("$issuingAuthorityId/tos.html")!!,
                mapOf("logo.png" to resources.getRawResource("$issuingAuthorityId/logo.png")!!)
            )
        }
    }

    data class GraphKey(
        val issuingAuthorityId: String,
        val documentId: String,
        val developerModeEnabled: Boolean
    )
}