package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.securearea.config.SecureAreaConfiguration
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
     * Key assertion parameter to [RequestCredentialsFlow.sendCredentials] is required.
     */
    val keyAssertionRequired: Boolean,

    /**
     * The configuration for the device-bound key for e.g. access control.
     *
     * This is Secure Area dependent.
     */
    val secureAreaConfiguration: SecureAreaConfiguration
) {
    companion object
}
