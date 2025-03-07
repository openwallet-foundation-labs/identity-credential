package org.multipaz.securearea.cloud

/**
 * Cloud Secure Area specific exception.
 */
open class CloudException : Exception {
    /**
     * Construct a new exception.
     *
     * @param message the message.
     */
    constructor(message: String) : super(message)

    /**
     * Construct a new exception.
     *
     * @param message the message.
     * @param cause the cause.
     */
    constructor(
        message: String,
        cause: Exception
    ) : super(message, cause)

    /**
     * Construct a new exception.
     *
     * @param cause the cause.
     */
    constructor(cause: Throwable) : super(cause)
}