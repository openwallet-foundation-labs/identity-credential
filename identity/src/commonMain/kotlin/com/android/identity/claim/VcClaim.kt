package com.android.identity.claim

import com.android.identity.documenttype.DocumentAttribute
import kotlinx.serialization.json.JsonElement

/**
 * A claim in a VC credential.
 *
 * @property claimName the claim name.
 * @property value the value of the claim
 */
data class VcClaim(
    override val displayName: String,
    override val attribute: DocumentAttribute?,
    val claimName: String,
    val value: JsonElement
) : Claim(displayName, attribute) {
    companion object {
    }
}