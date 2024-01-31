package com.android.identity.issuance

/**
 * The configuration to use when creating new [Credential.PendingAuthenticationKey] instances.
 * instances.
 */
data class AuthenticationKeyConfiguration(
    /**
     * The challenge to use when creating the device-bound key.
     */
    val challenge: ByteArray,

    // TODO: include access control specifiers
)
