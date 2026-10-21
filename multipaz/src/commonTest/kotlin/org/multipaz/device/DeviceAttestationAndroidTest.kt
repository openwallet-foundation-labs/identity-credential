package org.multipaz.device

import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.util.fromBase64Url
import org.multipaz.util.validateAndroidKeyAttestation
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class DeviceAttestationAndroidTest {
    @Test
    fun testValidationPixel7a() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_PIXEL7A)
        deviceAttestation.validate(
            DeviceAttestationValidationData(
                attestationChallenge = ATTESTATION_CHALLENGE_PIXEL7A.encodeToByteString(),
                softwareAccepted = false,
                softwareSecrets = setOf(),
                iosReleaseBuild = false,
                iosAppIdentifiers = setOf(),
                androidGmsAttestation = true,
                androidVerifiedBootGreen = true,
                androidRequiredKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
                androidAppSignatureCertificateDigests = setOf(
                    ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
                ),
                androidAppPackageNames = setOf("org.multipaz_credential.wallet"),
            ),
            validateAt = Instant.parse("2025-03-24T22:27:35Z")
        )
    }

    @Test
    fun testAssertionPixel7a() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_PIXEL7A)
        val deviceAssertion = DeviceAssertion.fromCbor(ASSERTION_PIXEL7A)
        deviceAttestation.validateAssertion(deviceAssertion)
    }

    @Test
    fun testValidationFactoryProvisionedWithExpiredIntermediateCa() = runTest {
        // Factory-provisioned chain (Pixel 3 / blueline) whose intermediate CAs expire on
        // 2028-07-20T20:33:28Z. Validating at 2030-01-01T00:00:00Z succeeds because
        // factory-provisioned CA expiration is ignored.
        val deviceAttestation = DeviceAttestationAndroid(
            certificateChain = X509CertChain(
                ATTESTATION_FACTORY_BLUELINE_PEMS.map { X509Cert.fromPem(it) }
            )
        )
        deviceAttestation.validate(
            DeviceAttestationValidationData(
                attestationChallenge = "challenge".encodeToByteString(),
                softwareAccepted = false,
                softwareSecrets = setOf(),
                iosReleaseBuild = false,
                iosAppIdentifiers = setOf(),
                androidGmsAttestation = true,
                androidVerifiedBootGreen = false,
                androidRequiredKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
                androidAppSignatureCertificateDigests = setOf(
                    ByteString("EDk47kU35Z6O55L2VFBPuDRvxrNG0LvEQV_DOfz8jsE".fromBase64Url())
                ),
                androidAppPackageNames = setOf(
                    "com.google.wireless.android.security.attestationverifier.collector"
                ),
            ),
            validateAt = Instant.parse("2030-01-01T00:00:00Z")
        )
    }

    @Test
    fun testAttestationEmulatorPixel3a() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_EMULATOR_PIXEL3A)
        deviceAttestation.validate(
            DeviceAttestationValidationData(
                attestationChallenge = ATTESTATION_CHALLENGE_EMULATOR_PIXEL3A.encodeToByteString(),
                softwareAccepted = false,
                softwareSecrets = setOf(),
                iosReleaseBuild = false,
                iosAppIdentifiers = setOf(),
                androidRequiredKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.SOFTWARE,
                androidGmsAttestation = false,
                androidVerifiedBootGreen = false,
                androidAppSignatureCertificateDigests = setOf(
                    ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
                ),
                androidAppPackageNames = setOf("org.multipaz_credential.wallet"),
            ),
            validateAt = Instant.parse("2025-05-05T00:00:00Z")
        )
    }

    @Test
    fun testAssertionEmulatorPixel3a() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_EMULATOR_PIXEL3A)
        val deviceAssertion = DeviceAssertion.fromCbor(ASSERTION_EMULATOR_PIXEL3A)
        deviceAttestation.validateAssertion(deviceAssertion)
    }

    @Test
    fun testNullChallengeReturnsExtractedChallenge() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_PIXEL7A) as DeviceAttestationAndroid
        val extractedChallenge = validateAndroidKeyAttestation(
            chain = deviceAttestation.certificateChain,
            challenge = null,
            requireGmsAttestation = true,
            requireVerifiedBootGreen = true,
            requireKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
            requireAppSignatureCertificateDigests = setOf(
                ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
            ),
            requireAppPackages = setOf("org.multipaz_credential.wallet"),
            validateAt = Instant.parse("2025-03-24T22:27:35Z")
        )
        assertEquals(ATTESTATION_CHALLENGE_PIXEL7A.encodeToByteString(), extractedChallenge)
    }

    @Test
    fun testWrongChallengeFails() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_PIXEL7A)
        assertFailsWith<DeviceAttestationException> {
            deviceAttestation.validate(
                DeviceAttestationValidationData(
                    attestationChallenge = "wrong_challenge".encodeToByteString(),
                    softwareAccepted = false,
                    softwareSecrets = setOf(),
                    iosReleaseBuild = false,
                    iosAppIdentifiers = setOf(),
                    androidGmsAttestation = true,
                    androidVerifiedBootGreen = true,
                    androidRequiredKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
                    androidAppSignatureCertificateDigests = setOf(
                        ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
                    ),
                    androidAppPackageNames = setOf("org.multipaz_credential.wallet"),
                ),
                validateAt = Instant.parse("2025-03-24T22:27:35Z")
            )
        }
    }

    @Test
    fun testWrongAppPackageNameFails() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_PIXEL7A)
        assertFailsWith<DeviceAttestationException> {
            deviceAttestation.validate(
                DeviceAttestationValidationData(
                    attestationChallenge = ATTESTATION_CHALLENGE_PIXEL7A.encodeToByteString(),
                    softwareAccepted = false,
                    softwareSecrets = setOf(),
                    iosReleaseBuild = false,
                    iosAppIdentifiers = setOf(),
                    androidGmsAttestation = true,
                    androidVerifiedBootGreen = true,
                    androidRequiredKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
                    androidAppSignatureCertificateDigests = setOf(
                        ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
                    ),
                    androidAppPackageNames = setOf("com.example.wrong.wallet"),
                ),
                validateAt = Instant.parse("2025-03-24T22:27:35Z")
            )
        }
    }

    @Test
    fun testWrongAppSignatureCertificateDigestFails() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_PIXEL7A)
        assertFailsWith<DeviceAttestationException> {
            deviceAttestation.validate(
                DeviceAttestationValidationData(
                    attestationChallenge = ATTESTATION_CHALLENGE_PIXEL7A.encodeToByteString(),
                    softwareAccepted = false,
                    softwareSecrets = setOf(),
                    iosReleaseBuild = false,
                    iosAppIdentifiers = setOf(),
                    androidGmsAttestation = true,
                    androidVerifiedBootGreen = true,
                    androidRequiredKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
                    androidAppSignatureCertificateDigests = setOf(
                        ByteString(ByteArray(32))
                    ),
                    androidAppPackageNames = setOf("org.multipaz_credential.wallet"),
                ),
                validateAt = Instant.parse("2025-03-24T22:27:35Z")
            )
        }
    }

    @Test
    fun testInsufficientKeyMintSecurityLevelFails() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_PIXEL7A)
        // ATTESTATION_PIXEL7A has TRUSTED_ENVIRONMENT; requiring STRONG_BOX must fail.
        assertFailsWith<DeviceAttestationException> {
            deviceAttestation.validate(
                DeviceAttestationValidationData(
                    attestationChallenge = ATTESTATION_CHALLENGE_PIXEL7A.encodeToByteString(),
                    softwareAccepted = false,
                    softwareSecrets = setOf(),
                    iosReleaseBuild = false,
                    iosAppIdentifiers = setOf(),
                    androidGmsAttestation = true,
                    androidVerifiedBootGreen = true,
                    androidRequiredKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.STRONG_BOX,
                    androidAppSignatureCertificateDigests = setOf(
                        ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
                    ),
                    androidAppPackageNames = setOf("org.multipaz_credential.wallet"),
                ),
                validateAt = Instant.parse("2025-03-24T22:27:35Z")
            )
        }
    }

    @Test
    fun testSoftwareAttestationRejectedWhenTeeRequired() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_EMULATOR_PIXEL3A)
        // ATTESTATION_EMULATOR_PIXEL3A is SOFTWARE level; requiring TRUSTED_ENVIRONMENT must fail.
        assertFailsWith<DeviceAttestationException> {
            deviceAttestation.validate(
                DeviceAttestationValidationData(
                    attestationChallenge = ATTESTATION_CHALLENGE_EMULATOR_PIXEL3A.encodeToByteString(),
                    softwareAccepted = false,
                    softwareSecrets = setOf(),
                    iosReleaseBuild = false,
                    iosAppIdentifiers = setOf(),
                    androidGmsAttestation = false,
                    androidVerifiedBootGreen = false,
                    androidRequiredKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
                    androidAppSignatureCertificateDigests = setOf(
                        ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
                    ),
                    androidAppPackageNames = setOf("org.multipaz_credential.wallet"),
                ),
                validateAt = Instant.parse("2025-05-05T00:00:00Z")
            )
        }
    }

    @Test
    fun testNonGreenVerifiedBootRejectedWhenGreenRequired() = runTest {
        // Factory blueline chain has UNVERIFIED boot state; requiring green boot state must fail.
        val deviceAttestation = DeviceAttestationAndroid(
            certificateChain = X509CertChain(
                ATTESTATION_FACTORY_BLUELINE_PEMS.map { X509Cert.fromPem(it) }
            )
        )
        assertFailsWith<DeviceAttestationException> {
            deviceAttestation.validate(
                DeviceAttestationValidationData(
                    attestationChallenge = "challenge".encodeToByteString(),
                    softwareAccepted = false,
                    softwareSecrets = setOf(),
                    iosReleaseBuild = false,
                    iosAppIdentifiers = setOf(),
                    androidGmsAttestation = true,
                    androidVerifiedBootGreen = true,
                    androidRequiredKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
                    androidAppSignatureCertificateDigests = setOf(
                        ByteString("EDk47kU35Z6O55L2VFBPuDRvxrNG0LvEQV_DOfz8jsE".fromBase64Url())
                    ),
                    androidAppPackageNames = setOf(
                        "com.google.wireless.android.security.attestationverifier.collector"
                    ),
                ),
                validateAt = Instant.parse("2025-01-01T00:00:00Z")
            )
        }
    }

    @Test
    fun testNonGmsRootRejectedWhenGmsAttestationRequired() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_EMULATOR_PIXEL3A)
        // ATTESTATION_EMULATOR_PIXEL3A is rooted in the Android Software Attestation root, not GMS.
        assertFailsWith<DeviceAttestationException> {
            deviceAttestation.validate(
                DeviceAttestationValidationData(
                    attestationChallenge = ATTESTATION_CHALLENGE_EMULATOR_PIXEL3A.encodeToByteString(),
                    softwareAccepted = false,
                    softwareSecrets = setOf(),
                    iosReleaseBuild = false,
                    iosAppIdentifiers = setOf(),
                    androidGmsAttestation = true,
                    androidVerifiedBootGreen = false,
                    androidRequiredKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.SOFTWARE,
                    androidAppSignatureCertificateDigests = setOf(
                        ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
                    ),
                    androidAppPackageNames = setOf("org.multipaz_credential.wallet"),
                ),
                validateAt = Instant.parse("2025-05-05T00:00:00Z")
            )
        }
    }

    @Test
    fun testBrokenCertificateChainSignatureFails() = runTest {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_PIXEL7A) as DeviceAttestationAndroid
        val certs = deviceAttestation.certificateChain.certificates
        // Drop the intermediate certificates so the leaf is followed directly by the root CA.
        val brokenChain = X509CertChain(listOf(certs.first(), certs.last()))
        assertFailsWith<IllegalStateException> {
            validateAndroidKeyAttestation(
                chain = brokenChain,
                challenge = ATTESTATION_CHALLENGE_PIXEL7A.encodeToByteString(),
                requireGmsAttestation = true,
                requireVerifiedBootGreen = true,
                requireKeyMintSecurityLevel = AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT,
                requireAppSignatureCertificateDigests = setOf(
                    ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
                ),
                requireAppPackages = setOf("org.multipaz_credential.wallet"),
                validateAt = Instant.parse("2025-03-24T22:27:35Z")
            )
        }
    }

    companion object {
        // Test data obtained by adding temporary server code to capture and save it.

        private const val ATTESTATION_CHALLENGE_PIXEL7A = "hJvYMWSqFp_75DYDqF6F13PB"

        @OptIn(ExperimentalEncodingApi::class)
        val ATTESTATION_PIXEL7A = Base64.decode("""
            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
            Miygh3sdEnQ68frVlA6hvALdiRw=
        """.trimIndent().replace("\n", ""))

        @OptIn(ExperimentalEncodingApi::class)
        val ASSERTION_PIXEL7A = Base64.decode("""
            om1hc3NlcnRpb25EYXRhU6JkdHlwZWVOb25jZWVub25jZUBxcGxhdGZvcm1Bc3NlcnRpb25YQG0O
            jZsCCv9aDPL2Ptz8lXIkjFZv4hz30JXNyycMTIX3szKiwQ3kA9zdlFdUTqhgy2XRmVZ+9T1qZJW6
            yVn2wzE=
        """.trimIndent().replace("\n", ""))

        private const val ATTESTATION_CHALLENGE_EMULATOR_PIXEL3A = "f34fEQlaaRg2QEUuo6U8L-YO"

        @OptIn(ExperimentalEncodingApi::class)
        val ATTESTATION_EMULATOR_PIXEL3A = Base64.decode("""
            omR0eXBlZ0FuZHJvaWRwY2VydGlmaWNhdGVDaGFpboNZAvUwggLxMIICmKADAgECAgEBMAoGCCqG
            SM49BAMCMIGIMQswCQYDVQQGEwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEVMBMGA1UECgwMR29v
            Z2xlLCBJbmMuMRAwDgYDVQQLDAdBbmRyb2lkMTswOQYDVQQDDDJBbmRyb2lkIEtleXN0b3JlIFNv
            ZnR3YXJlIEF0dGVzdGF0aW9uIEludGVybWVkaWF0ZTAeFw03MDAxMDEwMDAwMDBaFw00ODAxMDEw
            MDAwMDBaMB8xHTAbBgNVBAMTFEFuZHJvaWQgS2V5c3RvcmUgS2V5MFkwEwYHKoZIzj0CAQYIKoZI
            zj0DAQcDQgAEfEZ6swttifZj2hWdUUKtFzktRpVO7EwsrU6OlZMXc+pJKKRNHf4RdOuuNZcfvdIY
            /0i1HSB5IDNZ6Z3AmLkmj6OCAVkwggFVMA4GA1UdDwEB/wQEAwIHgDCCAUEGCisGAQQB1nkCAREE
            ggExMIIBLQICASwKAQACAgEsCgEABBhmMzRmRVFsYWFSZzJRRVV1bzZVOEwtWU8EADCB/qEFMQMC
            AQKiAwIBA6MEAgIBAKUFMQMCAQSqAwIBAb+DdwIFAL+FPQgCBgGVjdqnwL+FPgMCAQC/hUBMMEoE
            IAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQEACgECBCAAAAAAAAAAAAAAAAAAAAAA
            AAAAAAAAAAAAAAAAAAAAAL+FQQUCAwIi4L+FQgUCAwMWRb+FRVAETjBMMSYwJAQeb3JnLm11bHRp
            cGF6X2NyZWRlbnRpYWwud2FsbGV0AgIC8zEiBCBUSnGtYx/YYUvLb8cdO43vGVbl/LqYqFUCZEAO
            jhouHb+FTgMCAQC/hU8GAgQBNLL1MAAwCgYIKoZIzj0EAwIDRwAwRAIgWnC1SAmbUgnEOkNhtQJv
            vesYiB19IuNZNNgUcni89fMCIDbxZXm+G+m5FMBrysrqLPLNN05LqmP9YOUZdfo4Qy1pWQJ8MIIC
            eDCCAh6gAwIBAgICEAEwCgYIKoZIzj0EAwIwgZgxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxp
            Zm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MRUwEwYDVQQKDAxHb29nbGUsIEluYy4xEDAO
            BgNVBAsMB0FuZHJvaWQxMzAxBgNVBAMMKkFuZHJvaWQgS2V5c3RvcmUgU29mdHdhcmUgQXR0ZXN0
            YXRpb24gUm9vdDAeFw0xNjAxMTEwMDQ2MDlaFw0yNjAxMDgwMDQ2MDlaMIGIMQswCQYDVQQGEwJV
            UzETMBEGA1UECAwKQ2FsaWZvcm5pYTEVMBMGA1UECgwMR29vZ2xlLCBJbmMuMRAwDgYDVQQLDAdB
            bmRyb2lkMTswOQYDVQQDDDJBbmRyb2lkIEtleXN0b3JlIFNvZnR3YXJlIEF0dGVzdGF0aW9uIElu
            dGVybWVkaWF0ZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOueefhCY1msyyqRTImGzHCtkGaT
            gqlzJhP+rMv4ISdMIXSXSir+pblNf2bU4GUQZjW8U7ego6ZxWD7bPhGuEBSjZjBkMB0GA1UdDgQW
            BBQ//KzWGrE6noEguNUlHMVlux6RqTAfBgNVHSMEGDAWgBTIrel3TEXDo88NFhDkeUM6IVowzzAS
            BgNVHRMBAf8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIChDAKBggqhkjOPQQDAgNIADBFAiBLipt7
            7oK8wDOHri/AiZi03cONqycqRZ9pDMfDktQPjgIhAO7aAV229DLp1IQ7YkyUBO86fMy9Xvsiu+f+
            uXc/WT/7WQKPMIICizCCAjKgAwIBAgIJAKIFntEOQ1tXMAoGCCqGSM49BAMCMIGYMQswCQYDVQQG
            EwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzEVMBMGA1UE
            CgwMR29vZ2xlLCBJbmMuMRAwDgYDVQQLDAdBbmRyb2lkMTMwMQYDVQQDDCpBbmRyb2lkIEtleXN0
            b3JlIFNvZnR3YXJlIEF0dGVzdGF0aW9uIFJvb3QwHhcNMTYwMTExMDA0MzUwWhcNMzYwMTA2MDA0
            MzUwWjCBmDELMAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFjAUBgNVBAcMDU1vdW50
            YWluIFZpZXcxFTATBgNVBAoMDEdvb2dsZSwgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDEzMDEGA1UE
            AwwqQW5kcm9pZCBLZXlzdG9yZSBTb2Z0d2FyZSBBdHRlc3RhdGlvbiBSb290MFkwEwYHKoZIzj0C
            AQYIKoZIzj0DAQcDQgAE7l1ex+HA220Dpn7mthvsTWpdamguD/9/SQ59dx9EIm29sa/6FsvHrcV3
            0lacqrewLVQBXT5DKyqO107sSHVBpKNjMGEwHQYDVR0OBBYEFMit6XdMRcOjzw0WEOR5QzohWjDP
            MB8GA1UdIwQYMBaAFMit6XdMRcOjzw0WEOR5QzohWjDPMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0P
            AQH/BAQDAgKEMAoGCCqGSM49BAMCA0cAMEQCIDUho++LNEYenNVg8x1YiSBq3KNlQfYNns6KGYxm
            SGB7AiBNC/NR2TB8fVvaNTQdqEcbY6WFZTytTySn502vQX3xvw==
        """.trimIndent().replace("\n", ""))

        @OptIn(ExperimentalEncodingApi::class)
        val ASSERTION_EMULATOR_PIXEL3A = Base64.decode("""
            om1hc3NlcnRpb25EYXRhU6JkdHlwZWVOb25jZWVub25jZUBxcGxhdGZvcm1Bc3NlcnRpb25YQHRt
            c4So4V5Z6is6nMKHvaBCfYD5RIEoKNVlkY6Z2Ji68eiJdy7LTQJ7OIOSUbxjsOZQ5FgntUuZc8Af
            QCONlv0=
        """.trimIndent().replace("\n", ""))

        val ATTESTATION_FACTORY_BLUELINE_PEMS = listOf(
            """
            -----BEGIN CERTIFICATE-----
            MIICfzCCAiagAwIBAgIBATAKBggqhkjOPQQDAjApMRkwFwYDVQQFExBhMGI2M2Ez
            NTc0MzY3M2I3MQwwCgYDVQQMDANURUUwIBcNNzAwMTAxMDAwMDAwWhgPMjEwNjAy
            MDcwNjI4MTVaMB8xHTAbBgNVBAMMFEFuZHJvaWQgS2V5c3RvcmUgS2V5MFkwEwYH
            KoZIzj0CAQYIKoZIzj0DAQcDQgAEQ4ejMmmc5O9vcHpHjfo1EnLIuGseb9fTM26F
            PBQBMjUAo0zyVYJQpnExkAnFnpKkfZPAyk7gLdFEngSetIk01qOCAUUwggFBMA4G
            A1UdDwEB/wQEAwIHgDCCAS0GCisGAQQB1nkCAREEggEdMIIBGQIBAwoBAQIBBAoB
            AQQJY2hhbGxlbmdlBAAwgYO/hT0IAgYBZiKOLXa/hUVzBHEwbzFJMEcEQmNvbS5n
            b29nbGUud2lyZWxlc3MuYW5kcm9pZC5zZWN1cml0eS5hdHRlc3RhdGlvbnZlcmlm
            aWVyLmNvbGxlY3RvcgIBADEiBCAQOTjuRTflno7nkvZUUE+4NG/Gs0bQu8RBX8M5
            /PyOwTB4oQUxAwIBAqIDAgEDowQCAgEAqgMCAQG/g3cCBQC/hT4DAgEAv4VALDAq
            BAABAQAKAQIEIG6dDFvqLNqZ8+XHb7J0DN+Hk9HTY0Is0GXSK/Ciu1utv4VBBQID
            AV+Qv4VCBQIDAxS0v4VOBQIDAxRRv4VPBQIDAxS0MAoGCCqGSM49BAMCA0cAMEQC
            IA21HJafZI/wGy5lXuBtZt40jaXq6zlbXm9kPJm3CzFQAiA81uyzS8PTA/h9W5hC
            jrSGcsjHj3vZ0fDFxjq8JHAckg==
            -----END CERTIFICATE-----
            """.trimIndent(),
            """
            -----BEGIN CERTIFICATE-----
            MIICJTCCAaugAwIBAgIKBQFBMZUIaJgwUzAKBggqhkjOPQQDAjApMRkwFwYDVQQF
            ExBlMThjNGYyY2E2OTk3MzlhMQwwCgYDVQQMDANURUUwHhcNMTgwNzIzMjAzMzI4
            WhcNMjgwNzIwMjAzMzI4WjApMRkwFwYDVQQFExBhMGI2M2EzNTc0MzY3M2I3MQww
            CgYDVQQMDANURUUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATTv5kVHCdbVoeY
            YPnrPjNJ0QNtZMeJAKq0e0w5XfCMc2iHvHmZZ+hmWJud1S94wLKPJKx8OkJfpJg2
            fZoISK4Wo4G6MIG3MB0GA1UdDgQWBBRGZfPoN9ZqhkjAS/oWiLsbm6HO9TAfBgNV
            HSMEGDAWgBStaJfkd3MUTYzmNFYScunw3VEFvjAPBgNVHRMBAf8EBTADAQH/MA4G
            A1UdDwEB/wQEAwICBDBUBgNVHR8ETTBLMEmgR6BFhkNodHRwczovL2FuZHJvaWQu
            Z29vZ2xlYXBpcy5jb20vYXR0ZXN0YXRpb24vY3JsLzA1MDE0MTMxOTUwODY4OTgz
            MDUzMAoGCCqGSM49BAMCA2gAMGUCMQDtPMGkhcrmmUIm0iDXKgtlIGE/27j9e44P
            9lEoqbvgy4wT4pmfxYrP7S6JunE+2qcCMHt3SohaS7BaOu7awdlsdLoRO1avJURZ
            fJnen8gPP+oiqw/P5tWy59kgcDOEcohbvg==
            -----END CERTIFICATE-----
            """.trimIndent(),
            """
            -----BEGIN CERTIFICATE-----
            MIID0TCCAbmgAwIBAgIKA4gmZ2BliZaFnjANBgkqhkiG9w0BAQsFADAbMRkwFwYD
            VQQFExBmOTIwMDllODUzYjZiMDQ1MB4XDTE4MDcyMzIwMTM0MloXDTI4MDcyMDIw
            MTM0MlowKTEZMBcGA1UEBRMQZTE4YzRmMmNhNjk5NzM5YTEMMAoGA1UEDAwDVEVF
            MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEbRn8j/RF9mvI3WO2fMpitlBrovJ+SrHx
            4KBjW+Bcg1hnYTATavDaNk2O5ADA2KllkHLDSxIUTMMz6Zb7gJUd+/dC3sI701cR
            h0aWV9RzqnlgdR3IChRY4yuG9BSwoTfmo4G2MIGzMB0GA1UdDgQWBBStaJfkd3MU
            TYzmNFYScunw3VEFvjAfBgNVHSMEGDAWgBQ2YeEAfIgFCVGLRGxH/xpMyepPEjAP
            BgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwICBDBQBgNVHR8ESTBHMEWgQ6BB
            hj9odHRwczovL2FuZHJvaWQuZ29vZ2xlYXBpcy5jb20vYXR0ZXN0YXRpb24vY3Js
            L0U4RkExOTYzMTREMkZBMTgwDQYJKoZIhvcNAQELBQADggIBAEWkf0CP/lLv+Reh
            kJGy3VwI85YJ4ZshZVhUfUD0CcQR9VfGFjEEFN0ako0O+b7noTzZ+fwJqfMClmUk
            ZAnhJtYGJgGnF9bmTq5Itr/Cny0hmCkDdrqO52vi4ILz4gEkUVjLBY1A/UVipmVt
            kXgvEgdBUBmjv9fD7VP64vandbCAMtZpTCvu8cEAPgyh7EcaVhv1OEy7jahqJOIt
            poBvFySwQ0+1zF+7xJzLSQZESGb/eU3NimwLhNN/MFbZCQh3itdYZ0wqD6O3uCV/
            Jlx7SEakp+QOMk98IYA8276DJIgKkko3bLSmWrAP13zkea8geWCTFOfYfBo8g1in
            mipKNQL/nXTfixdA4OUYzQ/YKfrc5r+ux0EpxYiVEi53/aVMNeSbV+lOzbRctq1V
            wJPq4a00HBx1jwtZNmSjPWJjFkNu4bEU9bctCSHLsMsjEO3KGJRTGRATK7Dz43Qu
            rhwypvN/ZZl1K4NV+9RY4zNZV6ix9mWl+4cQAiPrJtrvohLtz7FM0LYZ3nz+Ak2j
            iCNQsKpOH3fuALhkQ6xZXVKoXmckEmXUc0wSSJ0qfkPD7SVpxjGeeiFmlWXM6ee1
            QFwKMcfaWQvqNTbKEihVSqV2BjXvxICsW93WEyqzt/COpcugGlvObeUTrqXuh59l
            o9ZGllgJgFJeGqByZDbQGawyB4Rc
            -----END CERTIFICATE-----
            """.trimIndent(),
            """
            -----BEGIN CERTIFICATE-----
            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
            -----END CERTIFICATE-----
            """.trimIndent()
        )
    }
}