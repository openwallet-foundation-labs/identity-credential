package org.multipaz.securearea.cloud

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A simple non-distributed and non-persistent [PassphraseFailureEnforcer].
 *
 * The policy implemented is that the client is locked out if N failed passphrase attempts
 * has been recorded in the last M seconds.
 *
 * Assuming the passphrase is a 7-digit numeric PIN, this means that no more than N
 * PIN guesses can be done in M seconds. For N=3 and M=60 seconds, this translates
 * into a maximum of three guesses per minute meaning the probability to guess the PIN
 * in one minute is 3 in 10 million. The time to try all 10 million combinations is just
 * over 6 years and 4 months.
 *
 * The data is kept in memory only and not persisted so this is not appropriate to
 * use in a production environment.
 *
 * @param lockoutNumFailedAttempts the number of failed passphrase attempts before a client is locked out.
 * @param lockoutDuration the duration of the period to consider.
 * @param clockFunction a function to give the current time (used only for unit tests).
 */
class SimplePassphraseFailureEnforcer(
    val lockoutNumFailedAttempts: Int = 3,
    val lockoutDuration: Duration = 60.seconds,
    private val clockFunction: () -> Instant = { Clock.System.now() }
): PassphraseFailureEnforcer {

    private val attemptsByClient = mutableMapOf<String, MutableList<Instant>>()

    private fun purgeOldAttempts(now: Instant) {
        val cutoffPoint = now.minus(lockoutDuration)

        // First remove all attempts before the cut-off point
        attemptsByClient.forEach { listEntry ->
            listEntry.value.removeIf { it <= cutoffPoint }
        }

        // Second, remove entries for clients where there are no attempts left
        attemptsByClient.entries.removeIf { mapEntry ->
            mapEntry.value.isEmpty()
        }
    }

    override fun recordFailedPassphraseAttempt(clientId: String) {
        val now = clockFunction()
        purgeOldAttempts(now)
        val attempts = attemptsByClient.getOrPut(clientId) { mutableListOf() }
        attempts.add(now)
    }

    override fun isLockedOut(clientId: String): Duration? {
        val now = clockFunction()
        purgeOldAttempts(now)
        val attempts = attemptsByClient.get(clientId)
        if (attempts == null) {
            return null
        }
        if (attempts.size < lockoutNumFailedAttempts) {
            return null
        }
        // Because clockFunction() is assumed to be monotonic, we are guaranteed that
        // the most distant attempt is the first element in the list.
        val mostDistantAttempt = attempts.first()

        // This invariant holds because purgeOldAttempts() would have removed this attempt if
        // it were before cutoffPoint
        val cutoffPoint = now.minus(lockoutDuration)
        check(mostDistantAttempt > cutoffPoint)
        val timeUntilNextAttempt = mostDistantAttempt - cutoffPoint
        return timeUntilNextAttempt
    }
}