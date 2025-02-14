package com.android.identity.device

import com.android.identity.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.crypto.Algorithm
import com.android.identity.securearea.SecureArea
import kotlinx.io.bytestring.ByteString

/**
 * Generates statements validating device/app/OS integrity. Details of these
 * statements are inherently platform-specific.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object DeviceCheck {
    actual suspend fun generateAttestation(
        secureArea: SecureArea,
        challenge: ByteString
    ): DeviceAttestationResult {
        val keySettings = AndroidKeystoreCreateKeySettings.Builder(challenge.toByteArray())
            .build()
        val keyInfo = secureArea.createKey(null, keySettings)
        return DeviceAttestationResult(
            deviceAttestationId = keyInfo.alias,
            deviceAttestation = DeviceAttestationAndroid(keyInfo.attestation.certChain!!)
        )
    }

    actual suspend fun generateAssertion(
        secureArea: SecureArea,
        deviceAttestationId: String,
        assertion: Assertion
    ): DeviceAssertion {
        val assertionData = assertion.toCbor()
        val signature = secureArea.sign(
            alias = deviceAttestationId,
            signatureAlgorithm = Algorithm.ES256,
            dataToSign = assertionData,
            keyUnlockData = null
        )
        return DeviceAssertion(
            assertionData = ByteString(assertionData),
            platformAssertion = ByteString(signature.toCoseEncoded())
        )
    }
}