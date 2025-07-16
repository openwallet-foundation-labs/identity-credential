package org.multipaz.mdoc.zkp

/**
 * Exception thrown when the ZK proof fails verification.
 *
 * @property message the exception message.
 */
class ProofVerificationFailureException(message: String) : Exception(message)
