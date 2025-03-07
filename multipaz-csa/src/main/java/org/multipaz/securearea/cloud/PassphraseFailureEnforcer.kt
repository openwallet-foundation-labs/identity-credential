package org.multipaz.securearea.cloud

import kotlin.time.Duration

/**
 * An interface for enforcing policy when a client fails to provide the right passphrase.
 *
 * The main purpose of this interface is to prevent an attacker from guessing a passphrase
 * by brute-forcing guessing. It does so by recording failed passphrase events and
 * using past events to determine if the user is locked out and for how long.
 *
 * This can be implemented in various ways, see [SimplePassphraseFailureEnforcer] for
 * the trivial non-persistent non-distributed version.
 */
interface PassphraseFailureEnforcer {

    /**
     * Records when an incorrect passphrase has been provided.
     *
     * @param clientId an unique identifier for the client.
     */
    fun recordFailedPassphraseAttempt(clientId: String)

    /**
     * Checks of a client is locked out because off to many failed passphrase attempts.
     *
     * @param clientId an unique identifier for the client.
     * @return null if not locked out, otherwise for how long they are locked out.
     */
    fun isLockedOut(clientId: String): Duration?
}