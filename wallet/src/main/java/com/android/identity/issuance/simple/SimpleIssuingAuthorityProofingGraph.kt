package com.android.identity.issuance.simple

import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice

/**
 * A builder of the graph of [Node]s that describes proofing workflows.
 *
 * A chain of steps without forks or merges can be described using a sequence of [add] calls.
 * A chain can end with (optionally) [addMultipleChoice] call that describes a fork based on
 * multiple-choice question. A merge can be created by creating a separate subgraph and adding
 * it to multiple chains using [addTail] method.
 */
class SimpleIssuingAuthorityProofingGraph() {
    private val chain = mutableListOf<SimpleNode>()
    private var last: Node? = null;

    fun add(node: SimpleNode): SimpleIssuingAuthorityProofingGraph {
        checkLast()
        chain.add(node)
        return this
    }

    fun addTail(node: Node): SimpleIssuingAuthorityProofingGraph {
        checkLast()
        last = node;
        return this
    }

    fun addMultipleChoice(node: MultipleChoiceNode): SimpleIssuingAuthorityProofingGraph {
        checkLast()
        last = node;
        return this
    }

    fun build(): Node {
        var node: Node? = last
        for (i in chain.indices.reversed()) {
            val current = chain[i]
            current.followUp = node
            node = current
        }
        if (node == null) {
            throw IllegalStateException("No nodes were added")
        }
        return node
    }

    private fun checkLast() {
        if (last != null) {
            throw IllegalStateException("addMultipleChoice or addTail was already called")
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
        private val request: EvidenceRequest): Node() {

        internal var followUp: Node? = null
        override val requests: List<EvidenceRequest>
            get() = listOf(request)
        override val followUps: Iterable<Node>
            get() = if (followUp == null) { listOf<Node>() } else { listOf(followUp!!) }
    }

    class MultipleChoiceNode(
        override val nodeId: String,
        private val request: EvidenceRequestQuestionMultipleChoice,
        private val followUpMap: Map<String, Node>): Node() {

        override val requests: List<EvidenceRequest>
            get() = listOf(request)
        override val followUps: Iterable<Node>
            get() = followUpMap.values

        override fun selectFollowUp(response: EvidenceResponse): Node? {
            val answer = (response as EvidenceResponseQuestionMultipleChoice).answer
            if (!request.possibleValues.contains(answer)) {
                throw IllegalStateException("Invalid answer: $answer")
            }
            return followUpMap[answer]
        }

    }
}