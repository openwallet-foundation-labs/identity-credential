package org.multipaz.graphhash

/**
 * Exception which is thrown when there is a loop in dependency graph and no member of the
 * loop has an explicit hash assigned by [GraphHasher.setAssignedHash].
 */
class UnassignedLoopException(
    val loop: List<Composite>
): Exception()