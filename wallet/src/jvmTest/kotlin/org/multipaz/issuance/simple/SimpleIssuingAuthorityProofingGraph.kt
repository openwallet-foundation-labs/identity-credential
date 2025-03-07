package org.multipaz.issuance.simple

import org.multipaz.issuance.evidence.EvidenceRequest
import org.multipaz.issuance.evidence.EvidenceRequestCreatePassphrase
import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnelType
import org.multipaz.issuance.evidence.EvidenceRequestIcaoPassiveAuthentication
import org.multipaz.issuance.evidence.EvidenceRequestMessage
import org.multipaz.issuance.evidence.EvidenceRequestNotificationPermission
import org.multipaz.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import org.multipaz.issuance.evidence.EvidenceRequestQuestionString
import org.multipaz.issuance.evidence.EvidenceResponse
import org.multipaz.issuance.evidence.EvidenceResponseIcaoNfcTunnelResult
import org.multipaz.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import org.multipaz.securearea.PassphraseConstraints
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.buildByteString

/**
 * A builder of the graph of [Node]s that describes proofing workflows.
 *
 * A chain of steps without forks or merges can be described using DSL inside
 * [SimpleIssuingAuthorityProofingGraph.create] call scope.
 *
 * Each node in the graph has associated id which can be used to look up [EvidenceResponse]
 * associated with this node in [SimpleIssuingAuthority.credentialGetConfiguration] and
 * [SimpleIssuingAuthority.checkEvidence] method implementations.
 */
class SimpleIssuingAuthorityProofingGraph {
    private val chain = mutableListOf<(Node?) -> Node>()

    companion object {
        fun create(init: SimpleIssuingAuthorityProofingGraph.() -> Unit): Node {
            val graph = SimpleIssuingAuthorityProofingGraph()
            graph.init()
            val first = graph.build(null)
            return first ?: throw IllegalStateException("No nodes were added")
        }
    }

    /** Sends [EvidenceRequestMessage]. */
    fun message(id: String, message: String, assets: Map<String, ByteArray>,
                acceptButtonText: String, rejectButtonText: String?) {
        val evidenceRequest = EvidenceRequestMessage(message,
            assets.mapValues { ByteString(it.value) },
            acceptButtonText, rejectButtonText)
        chain.add { followUp -> SimpleNode(id, followUp, evidenceRequest) }
    }

    /** Sends [EvidenceRequestNotificationPermission]. */
    fun requestNotificationPermission(
        id: String,
        permissionNotAvailableMessage: String,
        assets: Map<String, ByteArray>,
        grantPermissionButtonText: String,
        continueWithoutPermissionButtonText: String,
    ) {
        val evidenceRequest = EvidenceRequestNotificationPermission(
            permissionNotAvailableMessage,
            assets.mapValues { ByteString(it.value) },
            grantPermissionButtonText,
            continueWithoutPermissionButtonText)
        chain.add { followUp -> SimpleNode(id, followUp, evidenceRequest) }
    }

    /** Sends [EvidenceRequestQuestionString]. */
    fun question(id: String, message: String, assets: Map<String, ByteArray>,
                 defaultValue: String, acceptButtonText: String) {
        val evidenceRequest = EvidenceRequestQuestionString(message,
            assets.mapValues { ByteString(it.value) },
            defaultValue, acceptButtonText)
        chain.add { followUp -> SimpleNode(id, followUp, evidenceRequest) }
    }

    /** Sends [EvidenceRequestCreatePassphrase]. */
    fun createPassphrase(
        id: String,
        message: String,
        verifyMessage: String,
        assets: Map<String, ByteArray>,
        passphraseConstraints: PassphraseConstraints,
    ) {
        val evidenceRequest = EvidenceRequestCreatePassphrase(
            passphraseConstraints = passphraseConstraints,
            message = message,
            verifyMessage = verifyMessage,
            assets = assets.mapValues { ByteString(it.value) })
        chain.add { followUp -> SimpleNode(id, followUp, evidenceRequest) }
    }

    /**
     * Sends [EvidenceRequestQuestionMultipleChoice].
     *
     * Branches can be configured using [Choices.on] calls.
     */
    fun choice(id: String, message: String, assets: Map<String, ByteArray>,
               acceptButtonText: String, initChoices: Choices.() -> Unit) {
        val choices = Choices()
        choices.initChoices()
        val request = EvidenceRequestQuestionMultipleChoice(message,
            assets.mapValues { ByteString(it.value) },
            choices.choices, acceptButtonText)
        chain.add { followUp ->
            MultipleChoiceNode(id, request, choices.graphs.mapValues { graph ->
                graph.value.build(followUp)
            })
        }
    }

    /**
     * Sends [EvidenceRequestIcaoPassiveAuthentication].
     */
    fun icaoPassiveAuthentication(id: String, dataGroups: List<Int>) {
        val evidenceRequest = EvidenceRequestIcaoPassiveAuthentication(dataGroups)
        chain.add { followUp -> SimpleNode(id, followUp, evidenceRequest) }
    }

    /**
     * Sends [EvidenceRequestQuestionMultipleChoice].
     *
     * Branches can be configured using [IcaoChoices] methods.
     */
    fun icaoTunnel(id: String, dataGroups: List<Int>,
                   basicAuthentication: Boolean,
                   initChoices: IcaoChoices.() -> Unit) {
        chain.add { followUp ->
            val choices = IcaoChoices()
            choices.initChoices()
            val map = setOf(choices.noAuthenticationGraph, choices.activeAuthenticationGraph,
                choices.chipAuthenticationGraph).associateBy({graph -> graph}) { graph ->
                    graph.build(followUp)
                }
            IcaoNfcTunnelNode(id, dataGroups, basicAuthentication,
                successfulActiveAuthentication = map[choices.activeAuthenticationGraph]!!,
                successfulChipAuthentication = map[choices.chipAuthenticationGraph]!!,
                noAuthentication = map[choices.noAuthenticationGraph]!!
            )
        }
    }

    class Choices {
        val graphs = mutableMapOf<String, SimpleIssuingAuthorityProofingGraph>()
        val choices = mutableMapOf<String, String>()

        fun on(id: String, text: String, init: SimpleIssuingAuthorityProofingGraph.() -> Unit) {
            choices[id] = text
            val graph = SimpleIssuingAuthorityProofingGraph()
            graphs[id] = graph
            graph.init()
        }
    }

    class IcaoChoices {
        val activeAuthenticationGraph = SimpleIssuingAuthorityProofingGraph()
        var chipAuthenticationGraph = SimpleIssuingAuthorityProofingGraph()
        val noAuthenticationGraph = SimpleIssuingAuthorityProofingGraph()

        /**
         * Configures the branch that should be used when some form of authentication succeeded
         * beyond basic authentication (either Active Authentication or Chip Authentication).
         */
        fun whenAuthenticated(init: SimpleIssuingAuthorityProofingGraph.() -> Unit) {
            activeAuthenticationGraph.init()
            chipAuthenticationGraph = activeAuthenticationGraph
        }

        /**
         * Configures the branch that should be used when Chip Authentication succeeded.
         */
        fun whenChipAuthenticated(init: SimpleIssuingAuthorityProofingGraph.() -> Unit) {
            chipAuthenticationGraph.init()
        }

        /**
         * Configures the branch that should be used when Active Authentication succeeded.
         */
        fun whenActiveAuthenticated(init: SimpleIssuingAuthorityProofingGraph.() -> Unit) {
            activeAuthenticationGraph.init()
        }

        /**
         * Configures the branch that should be used when MRTD only supports basic authentication
         * and does not support any authentication that can verify that MRTD was not cloned.
         */
        fun whenNotAuthenticated(init: SimpleIssuingAuthorityProofingGraph.() -> Unit) {
            noAuthenticationGraph.init()
        }
    }

    private fun build(followUp: Node?): Node? {
        var node: Node? = followUp
        for (i in chain.indices.reversed()) {
            node = chain[i](node)
        }
        return node
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