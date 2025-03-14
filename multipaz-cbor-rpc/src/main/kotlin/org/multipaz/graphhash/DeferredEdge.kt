package org.multipaz.graphhash

class DeferredEdge(
    name: String,
    edgeKind: EdgeKind,
    val targetProducer: () -> Node
): Edge(name, edgeKind) {
    override val target: Node by lazy { targetProducer() }
}