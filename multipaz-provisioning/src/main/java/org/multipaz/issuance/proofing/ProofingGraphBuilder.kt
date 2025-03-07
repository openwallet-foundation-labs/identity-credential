package org.multipaz.issuance.proofing

import org.multipaz.issuance.evidence.EvidenceRequestCompletionMessage
import org.multipaz.issuance.evidence.EvidenceRequestCreatePassphrase
import org.multipaz.issuance.evidence.EvidenceRequestGermanEid
import org.multipaz.issuance.evidence.EvidenceRequestIcaoPassiveAuthentication
import org.multipaz.issuance.evidence.EvidenceRequestMessage
import org.multipaz.issuance.evidence.EvidenceRequestNotificationPermission
import org.multipaz.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import org.multipaz.issuance.evidence.EvidenceRequestQuestionString
import org.multipaz.issuance.evidence.EvidenceRequestSelfieVideo
import org.multipaz.issuance.evidence.EvidenceRequestSetupCloudSecureArea
import org.multipaz.issuance.evidence.EvidenceResponse
import org.multipaz.issuance.proofing.ProofingGraph.Node
import org.multipaz.securearea.PassphraseConstraints
import kotlinx.io.bytestring.ByteString

/**
 * A builder of the graph of [Node]s that describes proofing workflows.
 *
 * A chain of steps without forks or merges can be described using DSL inside
 * [ProofingGraphBuilder.create] call scope.
 *
 * Each node in the graph has associated id which can be used to look up [EvidenceResponse]
 * associated with this node.
 */
class ProofingGraphBuilder {
    private val chain = mutableListOf<(Node?) -> Node>()

    /** Sends [EvidenceRequestMessage]. */
    fun message(id: String, message: String, assets: Map<String, ByteString>,
                acceptButtonText: String, rejectButtonText: String?) {
        val evidenceRequest = EvidenceRequestMessage(message, assets, acceptButtonText, rejectButtonText)
        chain.add { followUp -> ProofingGraph.SimpleNode(id, followUp, evidenceRequest) }
    }

    /** Sends [EvidenceRequestCompletionMessage]. */
    fun completionMessage(
        id: String,
        messageTitle: String,
        message: String,
        assets: Map<String, ByteString>,
        acceptButtonText: String,
        rejectButtonText: String?,

    ) {
        val evidenceRequest =
            EvidenceRequestCompletionMessage(
                messageTitle,
                message,
                assets,
                acceptButtonText,
                rejectButtonText,
            )
        chain.add { followUp -> ProofingGraph.SimpleNode(id, followUp, evidenceRequest) }
    }

    /** Sends [EvidenceRequestNotificationPermission]. */
    fun requestNotificationPermission(
        id: String,
        permissionNotAvailableMessage: String,
        assets: Map<String, ByteString>,
        grantPermissionButtonText: String,
        continueWithoutPermissionButtonText: String,
    ) {
        val evidenceRequest = EvidenceRequestNotificationPermission(
            permissionNotAvailableMessage,
            assets,
            grantPermissionButtonText,
            continueWithoutPermissionButtonText)
        chain.add { followUp -> ProofingGraph.SimpleNode(id, followUp, evidenceRequest) }
    }

    /** Sends [EvidenceRequestQuestionString]. */
    fun question(id: String, message: String, assets: Map<String, ByteString>,
                 defaultValue: String, acceptButtonText: String) {
        val evidenceRequest = EvidenceRequestQuestionString(message, assets,
            defaultValue, acceptButtonText)
        chain.add { followUp -> ProofingGraph.SimpleNode(id, followUp, evidenceRequest) }
    }

    /** Sends [EvidenceRequestCreatePassphrase]. */
    fun createPassphrase(
        id: String,
        message: String,
        verifyMessage: String,
        assets: Map<String, ByteString>,
        passphraseConstraints: PassphraseConstraints,
    ) {
        val evidenceRequest = EvidenceRequestCreatePassphrase(
            passphraseConstraints = passphraseConstraints,
            message = message,
            verifyMessage = verifyMessage,
            assets = assets)
        chain.add { followUp -> ProofingGraph.SimpleNode(id, followUp, evidenceRequest) }
    }

    /** Sends [EvidenceRequestSetupCloudSecureArea]. */
    fun setupCloudSecureArea(
        id: String,
        cloudSecureAreaIdentifier: String,
        passphraseConstraints: PassphraseConstraints,
        message: String,
        verifyMessage: String,
        assets: Map<String, ByteString>,
    ) {
        val evidenceRequest = EvidenceRequestSetupCloudSecureArea(
            cloudSecureAreaIdentifier = cloudSecureAreaIdentifier,
            passphraseConstraints = passphraseConstraints,
            message = message,
            verifyMessage = verifyMessage,
            assets = assets)
        chain.add { followUp -> ProofingGraph.SimpleNode(id, followUp, evidenceRequest) }
    }

    /**
     * Sends [EvidenceRequestQuestionMultipleChoice].
     *
     * Branches can be configured using [Choices.on] calls.
     */
    fun choice(id: String, message: String, assets: Map<String, ByteString>,
               acceptButtonText: String, initChoices: Choices.() -> Unit) {
        val choices = Choices()
        choices.initChoices()
        val request = EvidenceRequestQuestionMultipleChoice(message, assets,
            choices.choices, acceptButtonText)
        chain.add { followUp ->
            ProofingGraph.MultipleChoiceNode(id, request, choices.graphs.mapValues { graph ->
                graph.value.build(followUp)
            })
        }
    }

    /**
     * Sends [EvidenceRequestIcaoPassiveAuthentication].
     */
    fun icaoPassiveAuthentication(id: String, dataGroups: List<Int>) {
        val evidenceRequest = EvidenceRequestIcaoPassiveAuthentication(dataGroups)
        chain.add { followUp -> ProofingGraph.SimpleNode(id, followUp, evidenceRequest) }
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
            ProofingGraph.IcaoNfcTunnelNode(id, dataGroups, basicAuthentication,
                successfulActiveAuthentication = map[choices.activeAuthenticationGraph]!!,
                successfulChipAuthentication = map[choices.chipAuthenticationGraph]!!,
                noAuthentication = map[choices.noAuthenticationGraph]!!
            )
        }
    }

    /** Sends [EvidenceRequestSelfieVideo]. */
    fun createSelfieRequest(id: String) {
        // For now, the list of poses is hardcoded here. In the future, this may come from the
        // issuing authority.
        val evidenceRequest = EvidenceRequestSelfieVideo(
            listOf(
                EvidenceRequestSelfieVideo.Poses.FRONT,
                EvidenceRequestSelfieVideo.Poses.SMILE,
                EvidenceRequestSelfieVideo.Poses.TILT_HEAD_UP,
                EvidenceRequestSelfieVideo.Poses.TILT_HEAD_DOWN
            )
        )
        chain.add { followUp -> ProofingGraph.SimpleNode(id, followUp, evidenceRequest) }
    }

    fun eId(id: String, tcTokenUrl: String, optionalComponents: List<String> = listOf()) {
        chain.add { followUp ->
            ProofingGraph.SimpleNode(id, followUp, EvidenceRequestGermanEid(tcTokenUrl, optionalComponents))
        }
    }

    class Choices {
        val graphs = mutableMapOf<String, ProofingGraphBuilder>()
        val choices = mutableMapOf<String, String>()

        fun on(id: String, text: String, init: ProofingGraphBuilder.() -> Unit) {
            choices[id] = text
            val graph = ProofingGraphBuilder()
            graphs[id] = graph
            graph.init()
        }
    }

    class IcaoChoices {
        val activeAuthenticationGraph = ProofingGraphBuilder()
        var chipAuthenticationGraph = ProofingGraphBuilder()
        val noAuthenticationGraph = ProofingGraphBuilder()

        /**
         * Configures the branch that should be used when some form of authentication succeeded
         * beyond basic authentication (either Active Authentication or Chip Authentication).
         */
        fun whenAuthenticated(init: ProofingGraphBuilder.() -> Unit) {
            activeAuthenticationGraph.init()
            chipAuthenticationGraph = activeAuthenticationGraph
        }

        /**
         * Configures the branch that should be used when Chip Authentication succeeded.
         */
        fun whenChipAuthenticated(init: ProofingGraphBuilder.() -> Unit) {
            chipAuthenticationGraph.init()
        }

        /**
         * Configures the branch that should be used when Active Authentication succeeded.
         */
        fun whenActiveAuthenticated(init: ProofingGraphBuilder.() -> Unit) {
            activeAuthenticationGraph.init()
        }

        /**
         * Configures the branch that should be used when MRTD only supports basic authentication
         * and does not support any authentication that can verify that MRTD was not cloned.
         */
        fun whenNotAuthenticated(init: ProofingGraphBuilder.() -> Unit) {
            noAuthenticationGraph.init()
        }
    }

    fun build(): ProofingGraph {
        val root = build(null) ?: throw IllegalStateException("No nodes were added")
        val map = mutableMapOf<String, Node>()
        val toTraverse = mutableListOf(root)
        val seen = mutableSetOf(root)
        while (toTraverse.isNotEmpty()) {
            val node = toTraverse.removeAt(toTraverse.lastIndex)
            check(!map.containsKey(node.nodeId)) {
                "Duplicate node id in proofing graph: '${node.nodeId}'"
            }
            map[node.nodeId] = node
            for (followUp in node.followUps) {
                if (!seen.contains(followUp)) {
                    seen.add(followUp)
                    toTraverse.add(followUp)
                }
            }
        }
        return ProofingGraph(root, map)
    }

    private fun build(followUp: Node?): Node? {
        var node: Node? = followUp
        for (i in chain.indices.reversed()) {
            node = chain[i](node)
        }
        return node
    }
}