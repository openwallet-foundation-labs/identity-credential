package com.android.identity.claim

import com.android.identity.cbor.DataItem
import com.android.identity.documenttype.DocumentAttribute

/**
 * A claim in an ISO mdoc credential.
 *
 * @property namespaceName the mdoc namespace.
 * @property dataElementName the data element name.
 * @property intentToRetain `true` if the requester intents to retain the value.
 * @property value the value of the claim.
 */
data class MdocClaim(
    override val displayName: String,
    override val attribute: DocumentAttribute?,
    val namespaceName: String,
    val dataElementName: String,
    val value: DataItem
) : Claim(displayName, attribute) {
    companion object {
    }
}