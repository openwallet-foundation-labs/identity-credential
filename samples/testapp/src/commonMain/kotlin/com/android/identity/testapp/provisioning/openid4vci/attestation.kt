package com.android.identity.testapp.provisioning.openid4vci

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.crypto.X509Cert
import org.multipaz.device.AssertionBindingKeys
import org.multipaz.device.DeviceAssertion
import org.multipaz.device.DeviceAttestation
import org.multipaz.device.DeviceAttestationIos
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.ProvisioningBackendSettings
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.securearea.KeyAttestation
import org.multipaz.util.isCloudKeyAttestation
import org.multipaz.util.validateAndroidKeyAttestation
import org.multipaz.util.validateCloudKeyAttestation

suspend fun validateDeviceAssertionBindingKeys(
    deviceAttestation: DeviceAttestation,
    keyAttestations: List<KeyAttestation>,
    deviceAssertion: DeviceAssertion,
    nonce: ByteString?
): AssertionBindingKeys {
    val settings = ProvisioningBackendSettings(BackendEnvironment.getInterface(Configuration::class)!!)
    if (BackendEnvironment.getInterface(ApplicationSupport::class) == null) {
        // No ApplicationSupport is indication that we are running on the server, not
        // locally in app. Device assertion validation is only meaningful or possible
        // on the server.
        deviceAttestation.validateAssertion(deviceAssertion)
    }
    val assertion = deviceAssertion.assertion as AssertionBindingKeys
    check(nonce == null || nonce == assertion.nonce)

    val keyList = keyAttestations.map { attestation ->
        val certChain = attestation.certChain
        if (certChain == null) {
            if (deviceAttestation !is DeviceAttestationIos) {
                throw IllegalArgumentException("key attestations are only optional for iOS")
            }
        } else {
            // TODO: check that what is claimed in the assertion matches what we see in key
            // attestations
            check(attestation.publicKey == certChain.certificates.first().ecPublicKey)
            if (isCloudKeyAttestation(certChain)) {
                val trustedRootKeys = getCloudSecureAreaTrustedRootKeys()
                validateCloudKeyAttestation(
                    attestation.certChain!!,
                    assertion.nonce,
                    trustedRootKeys.trustedKeys
                )
            } else {
                validateAndroidKeyAttestation(
                    certChain,
                    assertion.nonce,
                    settings.androidRequireGmsAttestation,
                    settings.androidRequireVerifiedBootGreen,
                    settings.androidRequireAppSignatureCertificateDigests
                )
            }
        }
        attestation.publicKey
    }

    if (keyList != assertion.publicKeys) {
        throw IllegalArgumentException("key list mismatch")
    }

    return assertion
}

private suspend fun getCloudSecureAreaTrustedRootKeys(): CloudSecureAreaTrustedRootKeys {
    return BackendEnvironment.cache(CloudSecureAreaTrustedRootKeys::class) { configuration, resources ->
        val certificateName = configuration.getValue("csa.certificate")
            ?: "cloud_secure_area/certificate.pem"
        val certificate = X509Cert.fromPem(resources.getStringResource(certificateName)!!)
        CloudSecureAreaTrustedRootKeys(
            trustedKeys = setOf(ByteString(Cbor.encode(certificate.ecPublicKey.toDataItem())))
        )
    }
}

internal data class CloudSecureAreaTrustedRootKeys(
    val trustedKeys: Set<ByteString>
)
