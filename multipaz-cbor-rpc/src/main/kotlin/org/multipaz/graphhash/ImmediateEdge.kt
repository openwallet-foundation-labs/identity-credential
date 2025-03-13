package org.multipaz.graphhash

class ImmediateEdge(
    name: String,
    edgeKind: EdgeKind,
    override val target: Node
): Edge(name, edgeKind)