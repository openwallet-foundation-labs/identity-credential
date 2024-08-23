package com.android.identity.documenttype

/**
 * A class representing a request for claims.
 *
 * @param claimsToRequest the claims to request.
 */
data class VcRequest(
    val claimsToRequest: List<DocumentAttribute>
)
