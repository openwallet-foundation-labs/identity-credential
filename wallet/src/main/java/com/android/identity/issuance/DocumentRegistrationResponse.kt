package com.android.identity.issuance

/**
 * The response from the application for creating the document.
 *
 * This is currently empty. In the future this will be used for attestations of keys
 * related to end-to-end encryption with the issuer.
 */
data class DocumentRegistrationResponse(val empty: Int = 0)
