package org.multipaz.request

/**
 * A request for a W3C Verifiable Credential.
 *
 * @property vct the Verifiable Credential Type.
 */
data class VcRequest(
    override val requester: Requester,
    override val requestedClaims: List<VcRequestedClaim>,
    val vct: String
): Request(requester, requestedClaims)
