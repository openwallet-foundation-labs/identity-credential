package com.android.identity.request

import com.android.identity.documenttype.DocumentAttribute

/**
 * Base class used for representing a request for a claim.
 *
 * @property displayName a short human readable string describing the claim.
 * @property attribute a [DocumentAttribute], if the claim is for a well-known attribute.
 */
sealed class RequestedClaim(
    open val displayName: String,
    open val attribute: DocumentAttribute?,
)
