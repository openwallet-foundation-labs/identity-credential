package com.android.identity.issuance

import java.security.cert.X509Certificate

/**
 * The response from the application for creating the credential.
 *
 * This is currently empty. In the future this will be used for attestations of keys
 * related to end-to-end encryption with the issuer.
 */
data class CredentialRegistrationResponse(val empty: Int = 0)
