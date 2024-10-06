package com.android.identity.appsupport.ui.consent

import com.android.identity.documenttype.DocumentAttribute

/**
 * Consent field for VC credentials.
 *
 * @param displayName the name to display in the consent prompt.
 * @param claimName the claim name.
 * @param attribute a [DocumentAttribute], if the claim is well-known.
 */
data class VcConsentField(
    override val displayName: String,
    override val attribute: DocumentAttribute?,
    val claimName: String
) : ConsentField(displayName, attribute) {

    companion object {
    }
}