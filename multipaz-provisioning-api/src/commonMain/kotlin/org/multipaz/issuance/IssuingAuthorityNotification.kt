package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable

/**
 * A data structure for capturing notification data emitted by the [IssuingAuthority].
 *
 * @property documentId the document.
 */
@CborSerializable
data class IssuingAuthorityNotification(
    val documentId: String
) {
    companion object
}