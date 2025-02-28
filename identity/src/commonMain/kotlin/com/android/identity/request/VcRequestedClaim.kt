package com.android.identity.request

import com.android.identity.documenttype.DocumentAttribute

/**
 * A request for a claim in a VC credential.
 *
 * @property claimName the claim name.
 */
data class VcRequestedClaim(
    override val displayName: String,
    override val attribute: DocumentAttribute?,
    val claimName: String,
): RequestedClaim(displayName, attribute) {
    companion object
}
