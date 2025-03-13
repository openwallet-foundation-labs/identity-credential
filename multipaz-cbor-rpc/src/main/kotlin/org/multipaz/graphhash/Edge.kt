package org.multipaz.graphhash

/**
 * An edge in the directed graph.
 */
sealed class Edge(
    val name: String,
    val edgeKind: EdgeKind,
) {
    abstract val target: Node
}