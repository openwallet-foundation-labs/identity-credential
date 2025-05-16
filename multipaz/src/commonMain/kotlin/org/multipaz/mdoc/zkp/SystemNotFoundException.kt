package org.multipaz.mdoc.zkp

/**
 * Exception thrown when a ZK system cannot be found.
 *
 * @property message the exception message.
 */
class SystemNotFoundException(message: String) : Exception(message) {}
