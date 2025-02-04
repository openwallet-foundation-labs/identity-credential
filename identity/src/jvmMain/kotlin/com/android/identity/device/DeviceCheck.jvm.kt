package com.android.identity.device

import com.android.identity.securearea.SecureArea
import kotlinx.io.bytestring.ByteString

/**
 * Generates statements validating device/app/OS integrity. Details of these
 * statements are inherently platform-specific.
 */
actual object DeviceCheck {
    actual suspend fun generateAttestation(
        secureArea: SecureArea,
        challenge: ByteString
    ): DeviceAttestationResult {
        return DeviceAttestationResult(
            "",
            DeviceAttestationJvm()
        )
    }

    actual suspend fun generateAssertion(
        secureArea: SecureArea,
        deviceAttestationId: String,
        assertion: Assertion
    ): DeviceAssertion {
        return DeviceAssertion(ByteString(), ByteString(assertion.toCbor()))
    }
}