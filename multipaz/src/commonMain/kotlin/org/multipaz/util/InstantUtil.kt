package org.multipaz.util

import kotlin.time.Instant

/**
 * Returns a new [Instant] with the fractional part of seconds removed.
 */
fun Instant.truncateToWholeSeconds() = Instant.fromEpochSeconds(this.epochSeconds)