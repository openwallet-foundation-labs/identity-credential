package org.multipaz.request

import org.multipaz.mdoc.zkp.ZkSystemSpec

/**
 * A request for an ISO mdoc.
 *
 * @property docType the ISO mdoc document type.
 */
data class MdocRequest(
    override val requester: Requester,
    override val requestedClaims: List<MdocRequestedClaim>,
    val docType: String,
    val zkSystemSpecs: List<ZkSystemSpec>? = null,
): Request(requester, requestedClaims)
