package com.android.identity.issuance

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcSignature
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.crypto.javaX509Certificates
import com.android.identity.device.AssertionBindingKeys
import com.android.identity.device.DeviceAssertion
import com.android.identity.device.DeviceAttestation
import com.android.identity.device.DeviceAttestationAndroid
import com.android.identity.device.DeviceAttestationIos
import com.android.identity.device.DeviceAttestationJvm
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.common.cache
import com.android.identity.securearea.AttestationExtension
import com.android.identity.securearea.KeyAttestation
import com.android.identity.util.AndroidAttestationExtensionParser
import com.android.identity.util.Logger
import com.android.identity.util.fromHex
import com.android.identity.util.toHex
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString

// TODO: move as much of this as possible into com.android.identity.device (and perhaps
// com.android.identity.crypto) package.

private const val TAG = "authenticationUtilities"

fun validateAndroidKeyAttestation(
    chain: X509CertChain,
    nonce: ByteString?,
    requireGmsAttestation: Boolean,
    requireVerifiedBootGreen: Boolean,
    requireAppSignatureCertificateDigests: List<String>,
) {
    check(chain.validate()) {
        "Certificate chain did not validate"
    }
    val x509certs = chain.javaX509Certificates
    val rootCertificatePublicKey = x509certs.last().publicKey

    if (requireGmsAttestation) {
        // Must match the well-known Google root
        check(
            GOOGLE_ROOT_ATTESTATION_KEY contentEquals rootCertificatePublicKey.encoded
        ) { "Unexpected attestation root" }
    }

    // Finally, check the Attestation Extension...
    try {
        val parser = AndroidAttestationExtensionParser(x509certs[0])

        // Challenge must match...
        check(nonce == null || nonce == ByteString(parser.attestationChallenge)) {
            "Challenge didn't match what was expected"
        }

        if (requireVerifiedBootGreen) {
            // Verified Boot state must VERIFIED
            check(
                parser.verifiedBootState ==
                        AndroidAttestationExtensionParser.VerifiedBootState.GREEN
            ) { "Verified boot state is not GREEN" }
        }

        if (requireAppSignatureCertificateDigests.isNotEmpty()) {
            check (parser.applicationSignatureDigests.size == requireAppSignatureCertificateDigests.size)
            { "Number Signing certificates mismatch" }
            for (n in 0..<parser.applicationSignatureDigests.size) {
                check (parser.applicationSignatureDigests[n] contentEquals requireAppSignatureCertificateDigests[n].fromHex())
                { "Signing certificate $n mismatch" }
            }
        }

        // Log the digests for easy copy-pasting into config file.
        Logger.d(
            TAG, "Accepting Android client with ${parser.applicationSignatureDigests.size} " +
                "signing certificates digests")
        for (n in 0..<parser.applicationSignatureDigests.size) {
            Logger.d(TAG, "Digest $n: ${parser.applicationSignatureDigests[n].toHex()}")
        }

    } catch (e: Exception) {
        throw IllegalStateException("Error parsing Android Attestation Extension", e)
    }
}

// This public key is from https://developer.android.com/training/articles/security-key-attestation
private val GOOGLE_ROOT_ATTESTATION_KEY =
    "30820222300d06092a864886f70d01010105000382020f003082020a0282020100afb6c7822bb1a701ec2bb42e8bcc541663abef982f32c77f7531030c97524b1b5fe809fbc72aa9451f743cbd9a6f1335744aa55e77f6b6ac3535ee17c25e639517dd9c92e6374a53cbfe258f8ffbb6fd129378a22a4ca99c452d47a59f3201f44197ca1ccd7e762fb2f53151b6feb2fffd2b6fe4fe5bc6bd9ec34bfe08239daafceb8eb5a8ed2b3acd9c5e3a7790e1b51442793159859811ad9eb2a96bbdd7a57c93a91c41fccd27d67fd6f671aa0b815261ad384fa37944864604ddb3d8c4f920a19b1656c2f14ad6d03c56ec060899041c1ed1a5fe6d3440b556bad1d0a152589c53e55d370762f0122eef91861b1b0e6c4c80927499c0e9bec0b83e3bc1f93c72c049604bbd2f1345e62c3f8e26dbec06c94766f3c128239d4f4312fad8123887e06becf567583bf8355a81feeabaf99a83c8df3e2a322afc672bf120b135158b6821ceaf309b6eee77f98833b018daa10e451f06a374d50781f359082966bb778b9308942698e74e0bcd24628a01c2cc03e51f0b3e5b4ac1e4df9eaf9ff6a492a77c1483882885015b422ce67b80b88c9b48e13b607ab545c723ff8c44f8f2d368b9f6520d31145ebf9e862ad71df6a3bfd2450959d653740d97a12f368b13ef66d5d0a54a6e2f5d9a6fef446832bc67844725861f093dd0e6f3405da89643ef0f4d69b6420051fdb93049673e36950580d3cdf4fbd08bc58483952600630203010001"
        .fromHex()

fun isCloudKeyAttestation(chain: X509CertChain): Boolean {
    return chain.certificates[0].javaX509Certificate
        .getExtensionValue(AttestationExtension.ATTESTATION_OID) != null
}

fun validateCloudKeyAttestation(
    chain: X509CertChain,
    nonce: ByteString,
    trustedRootKeys: Set<ByteString>
) {
    check(chain.validate()) {
        "Certificate chain did not validate"
    }
    val certificates = chain.certificates
    val leafX509Cert = certificates.first().javaX509Certificate
    val extensionDerEncodedString = leafX509Cert.getExtensionValue(AttestationExtension.ATTESTATION_OID)
        ?: throw IllegalStateException(
            "No attestation extension at OID ${AttestationExtension.ATTESTATION_OID}")

    val attestationExtension = try {
        val asn1InputStream = ASN1InputStream(extensionDerEncodedString);
        (asn1InputStream.readObject() as ASN1OctetString).octets
    } catch (e: Exception) {
        throw IllegalStateException("Error decoding attestation extension", e)
    }

    val challengeInAttestation = ByteString(AttestationExtension.decode(attestationExtension))
    if (challengeInAttestation != nonce) {
        throw IllegalStateException("Challenge in attestation does match expected nonce")
    }

    val rootPublicKey = ByteString(certificates.last().javaX509Certificate.publicKey.encoded)
    check(trustedRootKeys.contains(rootPublicKey)) {
        "Unexpected cloud attestation root"
    }
}

fun validateIosDeviceAttestation(attestation: DeviceAttestationIos) {
    // TODO, assume valid for now
}

fun validateDeviceAttestation(
    attestation: DeviceAttestation,
    clientId: String,
    settings: WalletServerSettings
) {
    when (attestation) {
        is DeviceAttestationAndroid -> {
            validateAndroidKeyAttestation(
                attestation.certificateChain,
                null, // TODO: enable: ByteString(clientId.toByteArray()),
                settings.androidRequireGmsAttestation,
                settings.androidRequireVerifiedBootGreen,
                settings.androidRequireAppSignatureCertificateDigests
            )
        }
        is DeviceAttestationIos -> {
            validateIosDeviceAttestation(attestation)
        }
        is DeviceAttestationJvm ->
            throw IllegalArgumentException("JVM attestations are not accepted")
    }
}

fun validateDeviceAssertion(attestation: DeviceAttestation, assertion: DeviceAssertion) {
    try {
        when (attestation) {
            is DeviceAttestationAndroid -> {
                val signature =
                    EcSignature.fromCoseEncoded(assertion.platformAssertion.toByteArray())
                if (!Crypto.checkSignature(
                        publicKey = attestation.certificateChain.certificates.first().ecPublicKey,
                        message = assertion.assertionData.toByteArray(),
                        algorithm = Algorithm.ES256,
                        signature = signature
                    )
                ) {
                    throw IllegalArgumentException("DeviceAssertion validation failed")
                }
            }

            is DeviceAttestationIos -> {
                // accept for now
            }

            is DeviceAttestationJvm ->
                throw IllegalArgumentException("JVM attestations are not accepted")
        }
    } catch(err: Exception) {
        err.printStackTrace()
        throw err
    }
}

suspend fun validateDeviceAssertionBindingKeys(
    env: FlowEnvironment,
    deviceAttestation: DeviceAttestation,
    keyAttestations: List<KeyAttestation>,
    deviceAssertion: DeviceAssertion,
    nonce: ByteString?
): AssertionBindingKeys {
    val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
    if (env.getInterface(ApplicationSupport::class) == null) {
        // No ApplicationSupport is indication that we are running on the server, not
        // locally in app. Device assertion validation is only meaningful or possible
        // on the server.
        validateDeviceAssertion(deviceAttestation, deviceAssertion)
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
                val trustedRootKeys = getCloudSecureAreaTrustedRootKeys(env)
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

private suspend fun getCloudSecureAreaTrustedRootKeys(
    env: FlowEnvironment
): CloudSecureAreaTrustedRootKeys {
    return env.cache(CloudSecureAreaTrustedRootKeys::class) { configuration, resources ->
        val certificateName = configuration.getValue("csa.certificate")
            ?: "cloud_secure_area/certificate.pem"
        val certificate = X509Cert.fromPem(resources.getStringResource(certificateName)!!)
        CloudSecureAreaTrustedRootKeys(
            trustedKeys = setOf(ByteString(certificate.javaX509Certificate.publicKey.encoded))
        )
    }
}

internal data class CloudSecureAreaTrustedRootKeys(
    val trustedKeys: Set<ByteString>
)
