package org.multipaz.graphhash

import kotlinx.io.bytestring.ByteString

/**
 * Directed graph node that has edges pointing to other nodes.
 *
 */
class Composite(val edges: List<Edge>, val extra: ByteString? = null): Node()