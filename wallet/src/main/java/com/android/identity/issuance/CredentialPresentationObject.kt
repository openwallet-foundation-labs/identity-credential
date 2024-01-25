package com.android.identity.issuance

import java.security.PublicKey

/**
 * This data structure contains a Credential Presentation Object minted by the issuer.
 */
data class CredentialPresentationObject(
    /**
     * The authentication key that this CPO is for.
     */
    val authenticationKey: PublicKey,

    /**
     * The CPO is not valid until this point in time (seconds since Epoch).
     */
    val validFromMillis: Long,

    /**
     * The CPO is not valid after this point in time (seconds since Epoch).
     */
    val validUntilMillis: Long,

    /**
     * The credential format-specific data.
     */
    val presentationData: ByteArray,
)