package org.multipaz.securearea

/**
 * Exception thrown when trying to use a key which has been invalidated.
 *
 * For example, this can happen with [AndroidKeystoreSecureArea] keys with user
 * authentication required and when the LSKF was removed from a device.
 */
class KeyInvalidatedException : Exception {
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