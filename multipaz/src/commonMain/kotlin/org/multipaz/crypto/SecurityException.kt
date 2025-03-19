package org.multipaz.crypto

/**
 * Base class for all security exceptions.
 *
 * @param message a textual message
 * @param cause the cause or `null`.
 */
sealed class SecurityException(
    message: String,
    cause: Throwable? = null
): Exception(message, cause)