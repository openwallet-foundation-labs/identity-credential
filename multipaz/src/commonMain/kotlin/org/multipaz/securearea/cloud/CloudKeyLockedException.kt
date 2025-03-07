package org.multipaz.securearea.cloud

import org.multipaz.securearea.KeyLockedException

/**
 * Specialization of [KeyLockedException] with extra detail for why a key is locked.
 */
class CloudKeyLockedException: KeyLockedException {
    /**
     * Possible reasons for why a key is locked
     */
    enum class Reason {
        WRONG_PASSPHRASE,
        USER_NOT_AUTHENTICATED
    }

    /**
     * The reason why the key didn't unlock.
     */
    val reason: Reason

    /**
     * Construct a new exception.
     *
     * @param reason the reason.
     * @param message the message.
     * @param cause the cause.
     */
    constructor(
        reason: Reason,
        message: String,
        cause: Exception
    ) : super(message, cause) {
        this.reason = reason
    }

    /**
     * Construct a new exception.
     *
     * @param reason the reason.
     * @param message the message.
     */
    constructor(reason: Reason, message: String) : super(message) {
        this.reason = reason
    }
}