package org.multipaz.nfc

/**
 * Exception thrown if a [NfcIsoTag] is removed while trying to communicate with it.
 */
class NfcTagLostException: Exception {
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
        cause: Throwable
    ) : super(message, cause)

    /**
     * Construct a new exception.
     *
     * @param cause the cause.
     */
    constructor(cause: Throwable) : super(cause)
}