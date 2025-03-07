package org.multipaz.securearea

/**
 * Exception thrown when trying to create a key with user authentication but
 * no screen lock has been set up.
 */
class ScreenLockRequiredException : Exception {
    /**
     * Construct a new exception.
     */
    constructor()

    /**
     * Construct a new exception.
     *
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
    constructor(cause: Exception) : super(cause)
}