package org.multipaz.issuance.proofing

import org.multipaz.issuance.evidence.EvidenceRequest
import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnelType
import org.multipaz.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import org.multipaz.issuance.evidence.EvidenceResponse
import org.multipaz.issuance.evidence.EvidenceResponseIcaoNfcTunnelResult
import org.multipaz.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import kotlinx.io.bytestring.buildByteString

/**
 * Graph that describes all the possible sequences of questions and answers for proofing.
 */
class ProofingGraph internal constructor(
    val root: Node,
    val map: Map<String, Node>  // nodeId no node
){
    companion object {
        fun create(init: ProofingGraphBuilder.() -> Unit): ProofingGraph {
            val graph = ProofingGraphBuilder()
            graph.init()
            return graph.build()
        }
    }

    abstract class Node {
        abstract val nodeId: String
        abstract val requests: List<EvidenceRequest>
        abstract val followUps: Iterable<Node>  // graph is finite and walkable
        open fun selectFollowUp(response: EvidenceResponse): Node? {
            val iterator = followUps.iterator()
            if (iterator.hasNext()) {
                val followUp = iterator.next()
                if (iterator.hasNext()) {
                    throw IllegalStateException(
                        "When there are multiple follow-ups, selectFollowUp must be implemented")
                }
                return followUp;
            }
            return null;
        }
    }

    class SimpleNode(
        override val nodeId: String,
        private val followUp: Node?,
        private val request: EvidenceRequest
    ): Node() {

        override val requests: List<EvidenceRequest>
            get() = listOf(request)
        override val followUps: Iterable<Node>
            get() = if (followUp == null) { listOf<Node>() } else { listOf(followUp) }
    }

    class MultipleChoiceNode(
        override val nodeId: String,
        private val request: EvidenceRequestQuestionMultipleChoice,
        private val followUpMap: Map<String, Node?>): Node() {

        override val requests: List<EvidenceRequest>
            get() = listOf(request)
        override val followUps: Iterable<Node>
            get() = followUpMap.values.filterNotNull()

        override fun selectFollowUp(response: EvidenceResponse): Node? {
            val answer = (response as EvidenceResponseQuestionMultipleChoice).answerId
            if (!request.possibleValues.contains(answer)) {
                throw IllegalStateException("Invalid answer: $answer")
            }
            return followUpMap[answer]
        }
    }

    class IcaoNfcTunnelNode(
        override val nodeId: String,
        val dataGroups: List<Int>,
        private val basicAuthentication: Boolean,
        private val successfulChipAuthentication: Node,
        private val successfulActiveAuthentication: Node,
        private val noAuthentication: Node): Node() {

        override val requests: List<EvidenceRequest>
            get() = listOf(
                EvidenceRequestIcaoNfcTunnel(
                    EvidenceRequestIcaoNfcTunnelType.HANDSHAKE,
                    !basicAuthentication, 0, buildByteString {})
            )
        override val followUps: Iterable<Node>
            get() = setOf(successfulActiveAuthentication, successfulChipAuthentication, noAuthentication)

        override fun selectFollowUp(response: EvidenceResponse): Node {
            val resp = response as EvidenceResponseIcaoNfcTunnelResult
            return when (resp.advancedAuthenticationType) {
                EvidenceResponseIcaoNfcTunnelResult.AdvancedAuthenticationType.NONE -> noAuthentication
                EvidenceResponseIcaoNfcTunnelResult.AdvancedAuthenticationType.CHIP -> successfulChipAuthentication
                EvidenceResponseIcaoNfcTunnelResult.AdvancedAuthenticationType.ACTIVE -> successfulActiveAuthentication
            }
        }
    }
}