package org.multipaz.claim

import org.multipaz.documenttype.DocumentAttribute
import org.multipaz.request.RequestedClaim
import kotlinx.datetime.TimeZone

/**
 * Base class used for representing a claim.
 *
 * @property displayName a short human readable string describing the claim.
 * @property attribute a [DocumentAttribute], if the claim is for a well-known attribute.
 */
sealed class Claim(
    open val displayName: String,
    open val attribute: DocumentAttribute?
) {
    /**
     * Returns the value of a claim as a human readable string.
     *
     * If [Claim.attribute] is set, its type is used when rendering for example to resolve integer options to strings.
     *
     * @param timeZone the time zone to use for rendering dates and times.
     * @return textual representation of the claim.
     */
    abstract fun render(timeZone: TimeZone = TimeZone.currentSystemDefault()): String
}
