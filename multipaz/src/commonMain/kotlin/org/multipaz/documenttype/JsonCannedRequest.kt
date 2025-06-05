package org.multipaz.documenttype

/**
 * A class representing a request for claims.
 *
 * @param vct the verifiable credential type.
 * @param claimsToRequest the claims to request.
 */
data class JsonCannedRequest(
    val vct: String,
    val claimsToRequest: List<DocumentAttribute>
)
