package org.multipaz.request

/**
 * A request for an ISO mdoc.
 *
 * @property docType the ISO mdoc document type.
 */
data class MdocRequest(
    override val requester: Requester,
    override val requestedClaims: List<MdocRequestedClaim>,
    val docType: String,
): Request(requester, requestedClaims)
