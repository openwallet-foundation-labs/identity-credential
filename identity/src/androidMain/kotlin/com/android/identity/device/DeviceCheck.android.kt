package com.android.identity.device

import com.android.identity.crypto.Algorithm
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureArea
import com.android.identity.util.toBase64Url
import kotlinx.io.bytestring.ByteString
import kotlin.random.Random

/**
 * Generates statements validating device/app/OS integrity. Details of these
 * statements are inherently platform-specific.
 */
actual object DeviceCheck {
    actual suspend fun generateAttestation(
        secureArea: SecureArea,
        clientId: String
    ): DeviceAttestationResult {
        val alias = "deviceCheck_" + Random.nextBytes(9).toBase64Url()
        // TODO: utilize clientId once we have access to AndroidSecureArea APIs here
        // and start checking it on the server
        secureArea.createKey(alias, CreateKeySettings())
        val keyInfo = secureArea.getKeyInfo(alias)
        return DeviceAttestationResult(
            deviceAttestationId = alias,
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
            keyUnlockData = null)
        return DeviceAssertion(
            assertionData = ByteString(assertionData),
            platformAssertion = ByteString(signature.toCoseEncoded())
        )
    }
}