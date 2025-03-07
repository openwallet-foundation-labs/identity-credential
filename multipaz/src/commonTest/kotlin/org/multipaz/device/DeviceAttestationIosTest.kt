package org.multipaz.device

import kotlinx.io.bytestring.encodeToByteString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test

class DeviceAttestationIosTest {
    private val attestationChallenge = "3og8indEDaOekzKXJ20EMBxW"
    private val deviceAttestation = DeviceAttestation.fromCbor(ATTESTATION)
    private val deviceAssertion = DeviceAssertion.fromCbor(ASSERTION)

    @Test
    fun testValidation() {
        deviceAttestation.validate(
            DeviceAttestationValidationData(
                attestationChallenge = attestationChallenge.encodeToByteString(),
                iosReleaseBuild = false,
                iosAppIdentifier = "74HWMG89B3.com.sorotokin.identity.testapp1",
                androidGmsAttestation = false,
                androidVerifiedBootGreen = false,
                androidAppSignatureCertificateDigests = listOf()
            )
        )
    }

    @Test
    fun testAssertion() {
        deviceAttestation.validateAssertion(deviceAssertion)
    }

    companion object {
        // Test data obtained by adding temporary server code to capture and save it.

        @OptIn(ExperimentalEncodingApi::class)
        val ATTESTATION = Base64.decode("""
            omRudWxsY0lvc2RibG9iWRUUo2NmbXRvYXBwbGUtYXBwYXR0ZXN0Z2F0dFN0bXSiY3g1Y4JZAzgwggM0
            MIICu6ADAgECAgYBlCntieAwCgYIKoZIzj0EAwIwTzEjMCEGA1UEAwwaQXBwbGUgQXBwIEF0dGVzdGF0
            aW9uIENBIDExEzARBgNVBAoMCkFwcGxlIEluYy4xEzARBgNVBAgMCkNhbGlmb3JuaWEwHhcNMjUwMTAy
            MDIxMDIwWhcNMjUxMDI5MDQxNDIwWjCBkTFJMEcGA1UEAwxAYTAwMmU3NmRkZDU5ZThmYWMwODQzNzlj
            MDY4NGNiNTQ5NzE5YzBhMmMzOGE5NmY0NTEwZjZhMjE0MDg2N2FmMDEaMBgGA1UECwwRQUFBIENlcnRp
            ZmljYXRpb24xEzARBgNVBAoMCkFwcGxlIEluYy4xEzARBgNVBAgMCkNhbGlmb3JuaWEwWTATBgcqhkjO
            PQIBBggqhkjOPQMBBwNCAATRiNf9blD1rZWC0nfTtonlEvfA3FYyTkGDJLVeIDPA2xBLcvP/s+oYzVo/
            kSrbAPFGBl76Cg9wf8yCf6tyMIc7o4IBPjCCATowDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCBPAw
            gYsGCSqGSIb3Y2QIBQR+MHykAwIBCr+JMAMCAQG/iTEDAgEAv4kyAwIBAb+JMwMCAQG/iTQsBCo3NEhX
            TUc4OUIzLmNvbS5zb3JvdG9raW4uaWRlbnRpdHkudGVzdGFwcDGlBgQEc2tzIL+JNgMCAQW/iTcDAgEA
            v4k5AwIBAL+JOgMCAQC/iTsDAgEAMFcGCSqGSIb3Y2QIBwRKMEi/ingIBAYxNy42LjG/iFAHAgUA////
            /r+KewcEBTIxRzkzv4p9CAQGMTcuNi4xv4p+AwIBAL+LDA8EDTIxLjcuOTMuMC4wLDAwMwYJKoZIhvdj
            ZAgCBCYwJKEiBCDo3YIxjKweNWPzPdX1MYPGhKrLDkJ8xiRoODzih5xQGjAKBggqhkjOPQQDAgNnADBk
            AjAQgtBoJfCJTRy5MmIbcwacO8Z2xGxV45Emmscdnvfhclicx6J/zE40Q7ZtHjFhb/4CMEGmwQ+twwKV
            fwsINloW3x/rltTsLhhNeyYQjn18sG30/ZU2ao9VVbTU/J5tr0E/VFkCRzCCAkMwggHIoAMCAQICEAm6
            xeG8QBrZ1FOVvDgaCFQwCgYIKoZIzj0EAwMwUjEmMCQGA1UEAwwdQXBwbGUgQXBwIEF0dGVzdGF0aW9u
            IFJvb3QgQ0ExEzARBgNVBAoMCkFwcGxlIEluYy4xEzARBgNVBAgMCkNhbGlmb3JuaWEwHhcNMjAwMzE4
            MTgzOTU1WhcNMzAwMzEzMDAwMDAwWjBPMSMwIQYDVQQDDBpBcHBsZSBBcHAgQXR0ZXN0YXRpb24gQ0Eg
            MTETMBEGA1UECgwKQXBwbGUgSW5jLjETMBEGA1UECAwKQ2FsaWZvcm5pYTB2MBAGByqGSM49AgEGBSuB
            BAAiA2IABK5bN6B3TXmyNY9A59HyJibxwl/vF4At6rOCalmHT/jSrRUleJqiZgQZEki2PLlnBp6Y02O9
            XjcPv6COMp6Ac6mF53Ruo1mi9m8p2zKvRV4hFljVZ6+eJn6yYU3CGmbOmaNmMGQwEgYDVR0TAQH/BAgw
            BgEB/wIBADAfBgNVHSMEGDAWgBSskRBTM72+aEH/pwyp5frq5eWKoTAdBgNVHQ4EFgQUPuNdHAQZqcm0
            MfiEdNbh4Vdy45swDgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49BAMDA2kAMGYCMQC7voiNc40FAs+8/WZt
            CVdQNbzWhyw/hDBJJint0fkU6HmZHJrota7406hUM/e2DQYCMQCrOO3QzIHtAKRSw7pE+ZNjZVP+zCl/
            LrTfn16+WkrKtplcS4IN+QQ4b3gHu1iUObdncmVjZWlwdFkOsjCABgkqhkiG9w0BBwKggDCAAgEBMQ8w
            DQYJYIZIAWUDBAIBBQAwgAYJKoZIhvcNAQcBoIAkgASCA+gxggRrMDICAQICAQEEKjc0SFdNRzg5QjMu
            Y29tLnNvcm90b2tpbi5pZGVudGl0eS50ZXN0YXBwMTCCA0ICAQMCAQEEggM4MIIDNDCCArugAwIBAgIG
            AZQp7YngMAoGCCqGSM49BAMCME8xIzAhBgNVBAMMGkFwcGxlIEFwcCBBdHRlc3RhdGlvbiBDQSAxMRMw
            EQYDVQQKDApBcHBsZSBJbmMuMRMwEQYDVQQIDApDYWxpZm9ybmlhMB4XDTI1MDEwMjAyMTAyMFoXDTI1
            MTAyOTA0MTQyMFowgZExSTBHBgNVBAMMQGEwMDJlNzZkZGQ1OWU4ZmFjMDg0Mzc5YzA2ODRjYjU0OTcx
            OWMwYTJjMzhhOTZmNDUxMGY2YTIxNDA4NjdhZjAxGjAYBgNVBAsMEUFBQSBDZXJ0aWZpY2F0aW9uMRMw
            EQYDVQQKDApBcHBsZSBJbmMuMRMwEQYDVQQIDApDYWxpZm9ybmlhMFkwEwYHKoZIzj0CAQYIKoZIzj0D
            AQcDQgAE0YjX/W5Q9a2VgtJ307aJ5RL3wNxWMk5BgyS1XiAzwNsQS3Lz/7PqGM1aP5Eq2wDxRgZe+goP
            cH/Mgn+rcjCHO6OCAT4wggE6MAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgTwMIGLBgkqhkiG92Nk
            CAUEfjB8pAMCAQq/iTADAgEBv4kxAwIBAL+JMgMCAQG/iTMDAgEBv4k0LAQqNzRIV01HODlCMy5jb20u
            c29yb3Rva2luLmlkZW50aXR5LnRlc3RhcHAxpQYEBHNrcyC/iTYDAgEFv4k3AwIBAL+JOQMCAQC/iToD
            AgEAv4k7AwIBADBXBgkqhkiG92NkCAcESjBIv4p4CAQGMTcuNi4xv4hQBwIFAP////6/insHBAUyMUc5
            M7+KfQgEBjE3LjYuMb+KfgMCAQC/iwwPBA0yMS43LjkzLjAuMCwwMDMGCSqGSIb3Y2QIAgQmMCShIgQg
            6N2CMYysHjVj8z3V9TGDxoSqyw5CfMYkaDg84oecUBowCgYIKoZIzj0EAwIDZwAwZAIwEILQaCXwiU0c
            uTJiG3MGnDvGdsRsVeORJprHHZ734XJYnMeif8xONEO2bR4xYW/+AjBBpsEPrcMClX8LCDZaFt8f65bU
            7C4YTXsmEI59fLBt9P2VNmqPVVW01Pyeba9BP1QwKAIBBAIBAQQgnx+6vf4V+pL+KXMG+2YHa6410Lnf
            dY/b+EeDxY11hFgwYAIBBQIBAQRYb1dGSVlYRHhEaFY1V0V1MVZhRFFocFVITnpDY2dJNHlkNi9oZTFO
            RmN4MXFRYm9PWXAvcGovBIGHSU1qbjFnalYvNi9zcWhlMHV6M1ZJTUhBNTdyNWkydEE9PTAOAgEGAgEB
            BAZBVFRFU1QwDwIBBwIBAQQHc2FuZGJveDAgAgEMAgEBBBgyMDI1LTAxLTAzVDAyOjEwOjIwLjc4Mlow
            IAIBFQIBAQQYMjAyNS0wNC0wM1QwMjoxMDoyMC43ODJaAAAAAAAAoIAwggOuMIIDVKADAgECAhB+AhJg
            2M53q3KlnfBoJ779MAoGCCqGSM49BAMCMHwxMDAuBgNVBAMMJ0FwcGxlIEFwcGxpY2F0aW9uIEludGVn
            cmF0aW9uIENBIDUgLSBHMTEmMCQGA1UECwwdQXBwbGUgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkxEzAR
            BgNVBAoMCkFwcGxlIEluYy4xCzAJBgNVBAYTAlVTMB4XDTI0MDIyNzE4Mzk1MloXDTI1MDMyODE4Mzk1
            MVowWjE2MDQGA1UEAwwtQXBwbGljYXRpb24gQXR0ZXN0YXRpb24gRnJhdWQgUmVjZWlwdCBTaWduaW5n
            MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IA
            BFQ3uILGT8UT6XpR5xJ0VeFLGpALmYvX1BaHaT8L2JPKizXqPVgjyWp1rfxMt3+SzCmZkJPZxtwtGADJ
            AyD0e0SjggHYMIIB1DAMBgNVHRMBAf8EAjAAMB8GA1UdIwQYMBaAFNkX/ktnkDhLkvTbztVXgBQLjz3J
            MEMGCCsGAQUFBwEBBDcwNTAzBggrBgEFBQcwAYYnaHR0cDovL29jc3AuYXBwbGUuY29tL29jc3AwMy1h
            YWljYTVnMTAxMIIBHAYDVR0gBIIBEzCCAQ8wggELBgkqhkiG92NkBQEwgf0wgcMGCCsGAQUFBwICMIG2
            DIGzUmVsaWFuY2Ugb24gdGhpcyBjZXJ0aWZpY2F0ZSBieSBhbnkgcGFydHkgYXNzdW1lcyBhY2NlcHRh
            bmNlIG9mIHRoZSB0aGVuIGFwcGxpY2FibGUgc3RhbmRhcmQgdGVybXMgYW5kIGNvbmRpdGlvbnMgb2Yg
            dXNlLCBjZXJ0aWZpY2F0ZSBwb2xpY3kgYW5kIGNlcnRpZmljYXRpb24gcHJhY3RpY2Ugc3RhdGVtZW50
            cy4wNQYIKwYBBQUHAgEWKWh0dHA6Ly93d3cuYXBwbGUuY29tL2NlcnRpZmljYXRlYXV0aG9yaXR5MB0G
            A1UdDgQWBBQrz0ke+88beQ7wrwIpE7UBFuF5NDAOBgNVHQ8BAf8EBAMCB4AwDwYJKoZIhvdjZAwPBAIF
            ADAKBggqhkjOPQQDAgNIADBFAiEAh6gJK3RfmEDFOpQhQRpdi6oJgNSGktXW0pmZ0HjHyrUCID9lU4wT
            LM+IMDSwR3Xol1PPz9P3RINVupdWXH2KBoEcMIIC+TCCAn+gAwIBAgIQVvuD1Cv/jcM3mSO1Wq5uvTAK
            BggqhkjOPQQDAzBnMRswGQYDVQQDDBJBcHBsZSBSb290IENBIC0gRzMxJjAkBgNVBAsMHUFwcGxlIENl
            cnRpZmljYXRpb24gQXV0aG9yaXR5MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDVQQGEwJVUzAeFw0x
            OTAzMjIxNzUzMzNaFw0zNDAzMjIwMDAwMDBaMHwxMDAuBgNVBAMMJ0FwcGxlIEFwcGxpY2F0aW9uIElu
            dGVncmF0aW9uIENBIDUgLSBHMTEmMCQGA1UECwwdQXBwbGUgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkx
            EzARBgNVBAoMCkFwcGxlIEluYy4xCzAJBgNVBAYTAlVTMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE
            ks5jvX2GsasoCjsc4a/7BJSAkaz2Md+myyg1b0RL4SHlV90SjY26gnyVvkn6vjPKrs0EGfEvQyX69L6z
            y4N+uqOB9zCB9DAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFLuw3qFYM4iapIqZ3r6966/ayySr
            MEYGCCsGAQUFBwEBBDowODA2BggrBgEFBQcwAYYqaHR0cDovL29jc3AuYXBwbGUuY29tL29jc3AwMy1h
            cHBsZXJvb3RjYWczMDcGA1UdHwQwMC4wLKAqoCiGJmh0dHA6Ly9jcmwuYXBwbGUuY29tL2FwcGxlcm9v
            dGNhZzMuY3JsMB0GA1UdDgQWBBTZF/5LZ5A4S5L0287VV4AUC489yTAOBgNVHQ8BAf8EBAMCAQYwEAYK
            KoZIhvdjZAYCAwQCBQAwCgYIKoZIzj0EAwMDaAAwZQIxAI1vpp+h4OTsW05zipJ/PXhTmI/02h9YHsN1
            Sv44qEwqgxoaqg2mZG3huZPo0VVM7QIwZzsstOHoNwd3y9XsdqgaOlU7PzVqyMXmkrDhYb6ASWnkXyup
            bOERAqrMYdk4t3NKMIICQzCCAcmgAwIBAgIILcX8iNLFS5UwCgYIKoZIzj0EAwMwZzEbMBkGA1UEAwwS
            QXBwbGUgUm9vdCBDQSAtIEczMSYwJAYDVQQLDB1BcHBsZSBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTET
            MBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwHhcNMTQwNDMwMTgxOTA2WhcNMzkwNDMwMTgx
            OTA2WjBnMRswGQYDVQQDDBJBcHBsZSBSb290IENBIC0gRzMxJjAkBgNVBAsMHUFwcGxlIENlcnRpZmlj
            YXRpb24gQXV0aG9yaXR5MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDVQQGEwJVUzB2MBAGByqGSM49
            AgEGBSuBBAAiA2IABJjpLz1AcqTtkyJygRMc3RCV8cWjTnHcFBbZDuWmBSp3ZHtfTjjTuxxEtX/1H7Yy
            Yl3J6YRbTzBPEVoA/VhYDKX1DyxNB0cTddqXl5dvMVztK517IDvYuVTZXpmkOlEKMaNCMEAwHQYDVR0O
            BBYEFLuw3qFYM4iapIqZ3r6966/ayySrMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMAoG
            CCqGSM49BAMDA2gAMGUCMQCD6cHEFl4aXTQY2e3v9GwOAEZLuN+yRhHFD/3meoyhpmvOwgPUnPWTxnS4
            at+qIxUCMG1mihDK1A3UT82NQz60imOlM27jbdoXt2QfyFMm+YhidDkLF1vLUagM6BgD56KyKAAAMYH9
            MIH6AgEBMIGQMHwxMDAuBgNVBAMMJ0FwcGxlIEFwcGxpY2F0aW9uIEludGVncmF0aW9uIENBIDUgLSBH
            MTEmMCQGA1UECwwdQXBwbGUgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkxEzARBgNVBAoMCkFwcGxlIElu
            Yy4xCzAJBgNVBAYTAlVTAhB+AhJg2M53q3KlnfBoJ779MA0GCWCGSAFlAwQCAQUAMAoGCCqGSM49BAMC
            BEcwRQIgXMvtRCUcK4qSKjwt2etil3uN1KW6MMV/8ZsYYYp69OoCIQCkBSU+Ubf2P4q3qsA0jeZgK303
            DSiFbyKhdxKhgmvueAAAAAAAAGhhdXRoRGF0YVikmnXkzs8lGdYheUnvor1+3/kLTXkIv1nnBWn8A/UP
            HkdAAAAAAGFwcGF0dGVzdGRldmVsb3AAIKAC523dWej6wIQ3nAaEy1SXGcCiw4qW9FEPaiFAhnrwpQEC
            AyYgASFYINGI1/1uUPWtlYLSd9O2ieUS98DcVjJOQYMktV4gM8DbIlggEEty8/+z6hjNWj+RKtsA8UYG
            XvoKD3B/zIJ/q3Iwhzs=
        """.trimIndent().replace("\n", ""))

        @OptIn(ExperimentalEncodingApi::class)
        val ASSERTION = Base64.decode("""
            om1hc3NlcnRpb25EYXRhU6JkbnVsbGVOb25jZWVub25jZUBxcGxhdGZvcm1Bc3NlcnRpb25YjKJpc2ln
            bmF0dXJlWEYwRAIgGPzVfFZj1m0TtZ88nc5uruubHAWAZY++pzeMQkjuaCMCIBAp9rur04YQBT15VmkL
            jYgCXPMuY/7UJs5F0d5UQ5LpcWF1dGhlbnRpY2F0b3JEYXRhWCWadeTOzyUZ1iF5Se+ivX7f+QtNeQi/
            WecFafwD9Q8eR0AAAAAB
        """.trimIndent().replace("\n", ""))
    }
}

