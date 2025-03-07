package org.multipaz.device

import org.multipaz.util.fromBase64Url
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test

class DeviceAttestationAndroidTest {
    @Test
    fun testValidationPixel7a() {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_PIXEL7A)
        deviceAttestation.validate(
            DeviceAttestationValidationData(
                attestationChallenge = ATTESTATION_CHALLENGE_PIXEL7A.encodeToByteString(),
                iosReleaseBuild = false,
                iosAppIdentifier = "",
                androidGmsAttestation = true,
                androidVerifiedBootGreen = true,
                androidAppSignatureCertificateDigests = listOf(
                    ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
                )
            )
        )
    }

    @Test
    fun testAssertionPixel7a() {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_PIXEL7A)
        val deviceAssertion = DeviceAssertion.fromCbor(ASSERTION_PIXEL7A)
        deviceAttestation.validateAssertion(deviceAssertion)
    }

    @Test
    fun testAttestationEmulatorPixel3a() {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_EMULATOR_PIXEL3A)
        deviceAttestation.validate(
            DeviceAttestationValidationData(
                attestationChallenge = ATTESTATION_CHALLENGE_EMULATOR_PIXEL3A.encodeToByteString(),
                iosReleaseBuild = false,
                iosAppIdentifier = "",
                androidGmsAttestation = false,
                androidVerifiedBootGreen = false,
                androidAppSignatureCertificateDigests = listOf(
                    ByteString("VEpxrWMf2GFLy2_HHTuN7xlW5fy6mKhVAmRADo4aLh0".fromBase64Url())
                )
            )
        )
    }

    @Test
    fun testAssertionEmulatorPixel3a() {
        val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION_EMULATOR_PIXEL3A)
        val deviceAssertion = DeviceAssertion.fromCbor(ASSERTION_EMULATOR_PIXEL3A)
        deviceAttestation.validateAssertion(deviceAssertion)
    }

    companion object {
        // Test data obtained by adding temporary server code to capture and save it.

        private const val ATTESTATION_CHALLENGE_PIXEL7A = "ax8S6Z5dKhX7ywLxLx3ZzrVo"

        @OptIn(ExperimentalEncodingApi::class)
        val ATTESTATION_PIXEL7A = Base64.decode("""
            omRudWxsZ0FuZHJvaWRwY2VydGlmaWNhdGVDaGFpboVZArEwggKtMIICU6ADAgECAgEBMAoGCCqGSM49
            BAMCMDkxKTAnBgNVBAMTIGE5ZjZmMWIxMTQzZGU2NmQ4OWU2Y2RhMmFkNzUwMDM4MQwwCgYDVQQKEwNU
            RUUwHhcNNzAwMTAxMDAwMDAwWhcNNDgwMTAxMDAwMDAwWjAfMR0wGwYDVQQDExRBbmRyb2lkIEtleXN0
            b3JlIEtleTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABF2n2Azc1UGn2RskZxQIViuQoP6qxARoTz4t
            S0hTDGJhJ7+nL1256C8auluiXngKSOznKBD7qA8RDt0EPgfjN7ajggFkMIIBYDAOBgNVHQ8BAf8EBAMC
            B4AwggFMBgorBgEEAdZ5AgERBIIBPDCCATgCAgEsCgEBAgIBLAoBAQQYYXg4UzZaNWRLaFg3eXdMeEx4
            M1p6clZvBAAwaL+FPQgCBgGULYTVVr+FRVgEVjBUMS4wLAQmY29tLmFuZHJvaWQuaWRlbnRpdHlfY3Jl
            ZGVudGlhbC53YWxsZXQCAgK2MSIEIFRKca1jH9hhS8tvxx07je8ZVuX8upioVQJkQA6OGi4dMIGhoQUx
            AwIBAqIDAgEDowQCAgEApQUxAwIBBKoDAgEBv4N3AgUAv4U+AwIBAL+FQEwwSgQgAD8a3p1HbmErAPKY
            PmrX3NFeaoDMLbsAjafWg57XOo8BAf8KAQAEIH/TlJ2uHBKhE9mBytDJvCVVDufCKfe4+7CXMqTgbUoC
            v4VBBQIDAiLgv4VCBQIDAxapv4VOBgIEATTaCb+FTwYCBAE02gkwCgYIKoZIzj0EAwIDSAAwRQIgJl/Y
            2+EW/97g6gEWnlROSTT3D4cqh80TODiNr1tEIdsCIQDfnaFpxs7qW4WKbooJh1CKoZfOg8TFm47GBHbp
            7e5Q3FkB5TCCAeEwggGGoAMCAQICEQCp9vGxFD3mbYnmzaKtdQA4MAoGCCqGSM49BAMCMCkxEzARBgNV
            BAoTCkdvb2dsZSBMTEMxEjAQBgNVBAMTCURyb2lkIENBMzAeFw0yNDEyMjAwNDQzMjVaFw0yNTAxMTYx
            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
            DqG8At2JHA==
        """.trimIndent().replace("\n", ""))

        @OptIn(ExperimentalEncodingApi::class)
        val ASSERTION_PIXEL7A = Base64.decode("""
            om1hc3NlcnRpb25EYXRhU6JkbnVsbGVOb25jZWVub25jZUBxcGxhdGZvcm1Bc3NlcnRpb25YQGKxzSMj
            nXr2kvwW8/b88CmEDGiYrTGMWzCflf7ok794bhED/HU4n/TBxABCvWsOLH3J67oeqOkUpksdePyjs9I=
        """.trimIndent().replace("\n", ""))

        private const val ATTESTATION_CHALLENGE_EMULATOR_PIXEL3A = "0F3iune5s98CkH3fGpwMDwL6"

        @OptIn(ExperimentalEncodingApi::class)
        val ATTESTATION_EMULATOR_PIXEL3A = Base64.decode("""
            omRudWxsZ0FuZHJvaWRwY2VydGlmaWNhdGVDaGFpboNZAv4wggL6MIICoaADAgECAgEBMAoGCCqGSM49
            BAMCMIGIMQswCQYDVQQGEwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEVMBMGA1UECgwMR29vZ2xlLCBJ
            bmMuMRAwDgYDVQQLDAdBbmRyb2lkMTswOQYDVQQDDDJBbmRyb2lkIEtleXN0b3JlIFNvZnR3YXJlIEF0
            dGVzdGF0aW9uIEludGVybWVkaWF0ZTAeFw03MDAxMDEwMDAwMDBaFw00ODAxMDEwMDAwMDBaMB8xHTAb
            BgNVBAMTFEFuZHJvaWQgS2V5c3RvcmUgS2V5MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8JsdsXwh
            80fngsiKvWj0n9FRfewzQd1NfUOin8TQ4/REw7sVGJPaTUKC2B00AxsaRBTvOBjyB9jTJfxTNISoHKOC
            AWIwggFeMA4GA1UdDwEB/wQEAwIHgDCCAUoGCisGAQQB1nkCAREEggE6MIIBNgICASwKAQACAgEsCgEA
            BBgwRjNpdW5lNXM5OENrSDNmR3B3TUR3TDYEADCCAQahBTEDAgECogMCAQOjBAICAQClBTEDAgEEqgMC
            AQG/g3cCBQC/hT0IAgYBlC+CncC/hT4DAgEAv4VATDBKBCAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
            AAAAAAAAAAEBAAoBAgQgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAC/hUEFAgMCIuC/hUIF
            AgMDFkW/hUVYBFYwVDEuMCwEJmNvbS5hbmRyb2lkLmlkZW50aXR5X2NyZWRlbnRpYWwud2FsbGV0AgIC
            tjEiBCBUSnGtYx/YYUvLb8cdO43vGVbl/LqYqFUCZEAOjhouHb+FTgMCAQC/hU8GAgQBNLL1MAAwCgYI
            KoZIzj0EAwIDRwAwRAIgB5ItkZArjon8LYga5di7BZRZ1Y+WlS8iXv/IHY1iLiUCIG0Ih9CfMgzUg/+a
            +qOz4NVNyaBSOAdXTKUf/HIUFmmwWQJ8MIICeDCCAh6gAwIBAgICEAEwCgYIKoZIzj0EAwIwgZgxCzAJ
            BgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MRUwEwYD
            VQQKDAxHb29nbGUsIEluYy4xEDAOBgNVBAsMB0FuZHJvaWQxMzAxBgNVBAMMKkFuZHJvaWQgS2V5c3Rv
            cmUgU29mdHdhcmUgQXR0ZXN0YXRpb24gUm9vdDAeFw0xNjAxMTEwMDQ2MDlaFw0yNjAxMDgwMDQ2MDla
            MIGIMQswCQYDVQQGEwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEVMBMGA1UECgwMR29vZ2xlLCBJbmMu
            MRAwDgYDVQQLDAdBbmRyb2lkMTswOQYDVQQDDDJBbmRyb2lkIEtleXN0b3JlIFNvZnR3YXJlIEF0dGVz
            dGF0aW9uIEludGVybWVkaWF0ZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOueefhCY1msyyqRTImG
            zHCtkGaTgqlzJhP+rMv4ISdMIXSXSir+pblNf2bU4GUQZjW8U7ego6ZxWD7bPhGuEBSjZjBkMB0GA1Ud
            DgQWBBQ//KzWGrE6noEguNUlHMVlux6RqTAfBgNVHSMEGDAWgBTIrel3TEXDo88NFhDkeUM6IVowzzAS
            BgNVHRMBAf8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIChDAKBggqhkjOPQQDAgNIADBFAiBLipt77oK8
            wDOHri/AiZi03cONqycqRZ9pDMfDktQPjgIhAO7aAV229DLp1IQ7YkyUBO86fMy9Xvsiu+f+uXc/WT/7
            WQKPMIICizCCAjKgAwIBAgIJAKIFntEOQ1tXMAoGCCqGSM49BAMCMIGYMQswCQYDVQQGEwJVUzETMBEG
            A1UECAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzEVMBMGA1UECgwMR29vZ2xlLCBJ
            bmMuMRAwDgYDVQQLDAdBbmRyb2lkMTMwMQYDVQQDDCpBbmRyb2lkIEtleXN0b3JlIFNvZnR3YXJlIEF0
            dGVzdGF0aW9uIFJvb3QwHhcNMTYwMTExMDA0MzUwWhcNMzYwMTA2MDA0MzUwWjCBmDELMAkGA1UEBhMC
            VVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFjAUBgNVBAcMDU1vdW50YWluIFZpZXcxFTATBgNVBAoMDEdv
            b2dsZSwgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDEzMDEGA1UEAwwqQW5kcm9pZCBLZXlzdG9yZSBTb2Z0
            d2FyZSBBdHRlc3RhdGlvbiBSb290MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7l1ex+HA220Dpn7m
            thvsTWpdamguD/9/SQ59dx9EIm29sa/6FsvHrcV30lacqrewLVQBXT5DKyqO107sSHVBpKNjMGEwHQYD
            VR0OBBYEFMit6XdMRcOjzw0WEOR5QzohWjDPMB8GA1UdIwQYMBaAFMit6XdMRcOjzw0WEOR5QzohWjDP
            MA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgKEMAoGCCqGSM49BAMCA0cAMEQCIDUho++LNEYe
            nNVg8x1YiSBq3KNlQfYNns6KGYxmSGB7AiBNC/NR2TB8fVvaNTQdqEcbY6WFZTytTySn502vQX3xvw==
        """.trimIndent().replace("\n", ""))

        @OptIn(ExperimentalEncodingApi::class)
        val ASSERTION_EMULATOR_PIXEL3A = Base64.decode("""
            om1hc3NlcnRpb25EYXRhU6JkbnVsbGVOb25jZWVub25jZUBxcGxhdGZvcm1Bc3NlcnRpb25YQLtkyOMD
            +C6VCcVpgZ6/JgWLmCgWEqiOugnKJV64isPtfvxKuEmelOYGCDxzdME7C74qORd1LuNSi88BlQ66lqo=
        """.trimIndent().replace("\n", ""))
    }
}