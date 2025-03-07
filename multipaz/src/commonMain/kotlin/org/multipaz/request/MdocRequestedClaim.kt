package org.multipaz.request

import org.multipaz.documenttype.DocumentAttribute

/**
 * A request for a claim in an ISO mdoc credential.
 *
 * @property namespaceName the mdoc namespace.
 * @property dataElementName the data element name.
 * @property intentToRetain `true` if the requester intends to retain the value.
 */
data class MdocRequestedClaim(
    override val displayName: String,
    override val attribute: DocumentAttribute?,
    val namespaceName: String,
    val dataElementName: String,
    val intentToRetain: Boolean
): RequestedClaim(displayName, attribute)