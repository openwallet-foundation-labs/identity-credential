package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.securearea.config.SecureAreaConfiguration
import kotlinx.io.bytestring.ByteString

/**
 * The configuration to use when creating new credentials.
 */
@CborSerializable
data class CredentialConfiguration(
    /**
     * The challenge to use when creating the device-bound key.
     */
    val challenge: ByteString,

    /**
     * The configuration for the device-bound key for e.g. access control.
     *
     * This is Secure Area dependent.
     */
    val secureAreaConfiguration: SecureAreaConfiguration
) {
    companion object
}
