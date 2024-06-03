package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable

/**
 * A data structure for capturing notification data.
 *
 * @property issuingAuthorityId the issuer.
 * @property documentId the document.
 */
@CborSerializable
data class WalletNotificationPayload(
    val issuingAuthorityId: String,
    val documentId: String
) {
    companion object
}