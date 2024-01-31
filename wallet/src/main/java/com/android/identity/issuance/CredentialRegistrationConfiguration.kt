package com.android.identity.issuance

/**
 * Issuer-specified configuration to be used for creating the credential.
 */
data class CredentialRegistrationConfiguration(
    /**
     * The credential identifier to be used in all future communications to refer
     * to the credential. This is guaranteed to be unique among all credentials
     * issued by the issuer.
     */
    val identifier: String,

    // TODO: include challenge/nonces for setting up E2EE encryption.
)
