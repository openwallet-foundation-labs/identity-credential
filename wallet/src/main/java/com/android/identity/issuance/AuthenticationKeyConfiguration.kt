package com.android.identity.issuance

/**
 * The configuration to use when creating new `AuthenticationKey` instances.
 */
data class AuthenticationKeyConfiguration(
    /**
     * The challenge to use when creating the device-bound key.
     */
    val challenge: ByteArray,

    // TODO: include access control specifiers
)
