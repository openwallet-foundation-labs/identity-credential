package com.android.identity.device

import com.android.identity.securearea.SecureArea
import kotlinx.io.bytestring.ByteString

/**
 * Generates statements validating device/OS/app integrity. Details of these
 * statements are inherently platform-specific.
 */
expect object DeviceCheck {
    /**
     * Generates a device attestation that proves the integrity of the device/OS/wallet app
     * and creates a certain opaque private key that resides securely on the device.
     *
     * The only operation that this opaque private key can be used for is generating
     * assertions using [generateAssertion] method.
     *
     * [secureArea] must be platform-specific [SecureArea]. (Other implementations of [SecureArea]
     * may or may not be used by this method depending on the platform).
     *
     * Validity of the attestation can be verified using [DeviceAttestation.validate].
     */
    suspend fun generateAttestation(
        secureArea: SecureArea,
        clientId: String
    ): DeviceAttestationResult

    /**
     * Generates [DeviceAssertion] - an [Assertion] which is signed using the key generated using
     * by [generateAttestation] method.
     *
     * Note that the exact format for the signature is platform-dependent.
     *
     * [secureArea] must be the same value as was passed to [generateAttestation] method.
     *
     * Validity of the assertion can be verified using [DeviceAttestation.validateAssertion] using
     * the [DeviceAttestation] object that corresponds to the given [deviceAttestationId].
     */
    suspend fun generateAssertion(
        secureArea: SecureArea,
        deviceAttestationId: String,
        assertion: Assertion
    ): DeviceAssertion
}