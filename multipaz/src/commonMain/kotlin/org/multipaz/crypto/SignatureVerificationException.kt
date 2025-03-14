package org.multipaz.crypto

/**
 * Exception thrown when a cryptographic signature fails to validate.
 *
 * @param message a textual message
 * @param cause the cause or `null`.
 */
class SignatureVerificationException(
    message: String,
    cause: Throwable? = null
): SecurityException(message, cause)
