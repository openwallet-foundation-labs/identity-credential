package org.multipaz.securearea

import org.multipaz.securearea.cloud.SimplePassphraseFailureEnforcer
import kotlin.time.Instant
import org.junit.Assert
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SimplePassphraseFailureEnforcerTest {

    @Test
    fun testEnforcer() {
        var now = Instant.fromEpochMilliseconds(0)

        val enforcer = SimplePassphraseFailureEnforcer(
            lockoutNumFailedAttempts = 4,
            lockoutDuration = 1.minutes,
            clockFunction = { now }
        )

        // First failed attempt at T = 0 sec -> no lockout
        now = Instant.fromEpochMilliseconds(0)
        Assert.assertNull(enforcer.isLockedOut("foo"))
        enforcer.recordFailedPassphraseAttempt("foo")

        // Second failed attempt at T = 15 sec -> no lockout
        now = now.plus(15.seconds)
        enforcer.recordFailedPassphraseAttempt("foo")
        Assert.assertNull(enforcer.isLockedOut("foo"))

        // Third failed attempt at T = 30 sec -> no lockout
        now = now.plus(15.seconds)
        enforcer.recordFailedPassphraseAttempt("foo")
        Assert.assertNull(enforcer.isLockedOut("foo"))

        // Fourth failed attempt at T = 44 sec
        //  -> LOCKOUT b/c at four attempts inside 1 minute.. can't try again until T = 1 minute
        now = now.plus(14.seconds)
        enforcer.recordFailedPassphraseAttempt("foo")
        Assert.assertEquals(16.seconds, enforcer.isLockedOut("foo")!!)

        // Still locked out at T = 45 sec
        now = now.plus(1.seconds)
        Assert.assertEquals(15.seconds, enforcer.isLockedOut("foo")!!)

        // Still locked out at T = 55 sec
        now = now.plus(10.seconds)
        Assert.assertEquals(5.seconds, enforcer.isLockedOut("foo")!!)

        // Not locked out at T = 60 sec b/c First failed attempt has now expired
        now = now.plus(5.seconds)
        Assert.assertNull(enforcer.isLockedOut("foo"))

        // Fifth failed attempt at T = 61 sec
        //  -> LOCKOUT b/c at four attempts inside 1 minute.. can't try again
        //     until T = 1 min 15 sec (when the Second failed attempt expires)
        now = now.plus(1.seconds)
        enforcer.recordFailedPassphraseAttempt("foo")
        Assert.assertEquals(14.seconds, enforcer.isLockedOut("foo")!!)

        // Not locked out at T = 1 min 15 sec sec b/c Second failed attempt has now expired
        now = now.plus(14.seconds)
        Assert.assertNull(enforcer.isLockedOut("foo"))
    }
}