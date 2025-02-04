package com.android.identity.device

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcSignature
import com.android.identity.crypto.X509CertChain
import com.android.identity.util.validateAndroidKeyAttestation
import kotlinx.io.bytestring.encodeToByteString

/**
 * On Android we create a private key in secure area and use its key attestation as the
 * device attestation.
 */
data class DeviceAttestationAndroid(
    val certificateChain: X509CertChain
) : DeviceAttestation() {
    override fun validate(validationData: DeviceAttestationValidationData) {
        try {
            validateAndroidKeyAttestation(
                certificateChain,
                validationData.attestationChallenge,
                validationData.androidGmsAttestation,
                validationData.androidVerifiedBootGreen,
                validationData.androidAppSignatureCertificateDigests
            )
        } catch (err: Exception) {
            throw DeviceAttestationException("Failed Android device attestation", err)
        }
    }

    override fun validateAssertion(assertion: DeviceAssertion) {
        val signature =
            EcSignature.fromCoseEncoded(assertion.platformAssertion.toByteArray())
        if (!Crypto.checkSignature(
                publicKey = certificateChain.certificates.first().ecPublicKey,
                message = assertion.assertionData.toByteArray(),
                algorithm = Algorithm.ES256,
                signature = signature
            )
        ) {
            throw DeviceAssertionException("DeviceAssertion signature validation failed")
        }
    }
}