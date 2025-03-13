package org.multipaz.graphhash

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString

/**
 * A class that computes hash for the nodes of the graph.
 *
 * Node hash depends on the node content and transitively on all the node's edges and nodes
 * pointed by the edges.
 *
 * Node hash helps detecting relevant changes in the graph. When two graphs are the same, hashes
 * for all the corresponding nodes will be the same. When two graphs differ, node hashes will be
 * different only for nodes that differ or which directly or indirectly point to nodes that differ.
 *
 * Some nodes can have "assigned hash". For the purposes of anther node's hash, a node with
 * assigned hash is considered changed if and only if its assigned hash changes.
 *
 * Loops in the graph are allowed only if one member of a loop has an assigned hash.
 */
class GraphHasher(
    val hashBuilderFactory: () -> HashBuilder
) {
    private val computed = mutableMapOf<Node, ByteString>()
    private val assigned = mutableMapOf<Node, ByteString>()

    /**
     * Sets assigned hash for the given node.
     */
    fun setAssignedHash(node: Node, assignedHash: ByteString) {
        assigned[node] = assignedHash
        if (node is Leaf) {
            computed[node] = assignedHash
        }
    }

    /**
     * Computes hash for the given node.
     *
     * Note: for nodes with assigned hashes this computes their hash in the regular fashion.
     */
    fun hash(node: Node): ByteString = hash(node, mutableListOf())

    private fun hash(node: Node, path: MutableList<Composite>): ByteString {
        val computedHash = computed[node]
        if (computedHash != null && computedHash !== VISITING) {
            return computedHash
        }
        if (path.isNotEmpty()) {
            val assignedHash = assigned[node]
            if (assignedHash != null) {
                return assignedHash
            }
        }
        if (computedHash === VISITING) {
            var i = path.lastIndex
            while (i >= 0) {
                if (path[i] === node) {
                    // Make a copy
                    val loop = mutableListOf<Composite>()
                    loop.addAll(path.subList(i, path.size))
                    throw UnassignedLoopException(loop.toList())
                }
                i--
            }
            throw IllegalStateException("Unexpected state")
        }
        val hashBuilder = hashBuilderFactory()
        when (node) {
            is Leaf -> {
                hashBuilder.update(LEAF_MARK)
                hashBuilder.update(node.name.encodeToByteString())
            }
            is Composite -> try {
                computed[node] = VISITING
                path.add(node)
                hashBuilder.update(COMPOSITE_MARK)
                if (node.extra != null) {
                    hashBuilder.update(node.extra)
                }
                val edges = node.edges.toMutableList()
                edges.sortWith { a, b -> a.name.compareTo(b.name) }
                var last: String? = null
                for (edge in edges) {
                    // Must be sorted without duplicates
                    check(last == null || last < edge.name)
                    last = edge.name
                    hashBuilder.update(last.encodeToByteString())
                    hashBuilder.update(END_MARK)
                    hashBuilder.update(edge.edgeKind.mark)
                    hashBuilder.update(hash(edge.target, path))
                }
            } finally {
                computed.remove(node)
                path.removeLast()
            }
        }
        val hash = hashBuilder.build()
        computed[node] = hash
        return hash
    }

    companion object {
        val VISITING = ByteString()
        val END_MARK = ByteString(0)
        val LEAF_MARK = ByteString(1)
        val COMPOSITE_MARK = ByteString(2)
    }
}