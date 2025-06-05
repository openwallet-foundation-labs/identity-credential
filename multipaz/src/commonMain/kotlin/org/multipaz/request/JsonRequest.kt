package org.multipaz.request

/**
 * A request for a JSON-based credential.
 *
 * @property vct the Verifiable Credential Type.
 */
data class JsonRequest(
    override val requester: Requester,
    override val requestedClaims: List<JsonRequestedClaim>,
    val vct: String
): Request(requester, requestedClaims)
