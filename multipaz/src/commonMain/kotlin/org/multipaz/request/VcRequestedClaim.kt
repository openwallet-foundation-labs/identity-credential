package org.multipaz.request

import org.multipaz.documenttype.DocumentAttribute

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
