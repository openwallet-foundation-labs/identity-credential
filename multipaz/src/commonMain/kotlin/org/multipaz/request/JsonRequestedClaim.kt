package org.multipaz.request

import kotlinx.serialization.json.JsonArray
import org.multipaz.documenttype.DocumentAttribute

/**
 * A request for a claim in a JSON-based credential.
 *
 * @property claimPath the claims path pointer.
 */
data class JsonRequestedClaim(
    override val displayName: String,
    override val attribute: DocumentAttribute?,
    val claimPath: JsonArray,
): RequestedClaim(displayName, attribute) {
    companion object
}
