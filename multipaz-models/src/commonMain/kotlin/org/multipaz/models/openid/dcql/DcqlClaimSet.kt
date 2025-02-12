package org.multipaz.models.openid.dcql

/**
 * DCQL Claim Set.
 *
 * Reference: OpenID4VP 1.0 Section 6.4.
 *
 * @property claimIdentifiers a list of claim identifiers.
 */
data class DcqlClaimSet(
    val claimIdentifiers: List<String>
)