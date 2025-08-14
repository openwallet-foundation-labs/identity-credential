package org.multipaz.request

import kotlinx.serialization.json.JsonArray

/**
 * Base class used for representing a request for a claim.
 *
 * @property id the identifier for the claim or `null`.
 * @property values A set of acceptable values or `null` to not match on value.
 */
sealed class RequestedClaim(
    open val id: String? = null,
    open val values: JsonArray? = null,
)
