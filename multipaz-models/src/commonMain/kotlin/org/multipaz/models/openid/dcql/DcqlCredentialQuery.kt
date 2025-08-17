package org.multipaz.models.openid.dcql

import kotlinx.serialization.json.JsonObject
import org.multipaz.request.RequestedClaim

/**
 * DCQL Credential Query.
 *
 * Reference: OpenID4VP 1.0 Section 6.1.
 *
 * @property id the assigned identifier for the Credential Query.
 * @property format the requested format of the credential e.g. `mso_mdoc` or `dc+sd-jwt`.
 * @property mdocDocType the ISO mdoc doctype or `null` if format isn't `mso_mdoc`.
 * @property vctValues the array of Verifiable Credential Types or `null` if format isn't `dc+sd-jwt`.
 * @property claims a list of claims being requested.
 * @property claimSets a list of claim sets.
 */
data class DcqlCredentialQuery(
    val id: String,
    val format: String,
    val meta: JsonObject,

    // from meta
    val mdocDocType: String? = null,
    val vctValues: List<String>? = null,

    val claims: List<RequestedClaim>,
    val claimSets: List<DcqlClaimSet>,

    internal val claimIdToClaim: Map<String, RequestedClaim>
)