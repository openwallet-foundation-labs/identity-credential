package com.android.identity.issuance

import com.android.identity.crypto.EcPublicKey

/**
 * This data structure contains a Document Presentation Object minted by the issuer.
 */
data class DocumentPresentationObject(
    /**
     * The authentication key that this CPO is for.
     */
    val authenticationKey: EcPublicKey,

    /**
     * The CPO is not valid until this point in time (seconds since Epoch).
     */
    val validFromMillis: Long,

    /**
     * The CPO is not valid after this point in time (seconds since Epoch).
     */
    val validUntilMillis: Long,

    /**
     * The document format-specific data.
     */
    val presentationData: ByteArray,
)