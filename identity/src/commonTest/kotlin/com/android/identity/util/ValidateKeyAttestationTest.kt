package com.android.identity.util

import com.android.identity.cbor.Cbor
import com.android.identity.crypto.X509CertChain
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test

class ValidateKeyAttestationTest {
    // NB: Android key attestation is covered by DeviceAttestationAndroidTest

    @Test
    fun testCloudKeyAttestation() {
        val certChain = X509CertChain.fromDataItem(Cbor.decode(CLOUD_CERT_CHAIN))
        println(certChain.certificates.first().toPem())
        validateCloudKeyAttestation(
            chain = certChain,
            nonce = CLOUD_NONCE,
            trustedRootKeys = setOf(ByteString(CLOUD_ROOT_KEY))
        )
    }

    companion object {
        // Test data obtained by adding temporary server code to capture and save it.
        val CLOUD_NONCE = "awLuB0C4LL1Rg5lhmrlmWf".encodeToByteString()

        @OptIn(ExperimentalEncodingApi::class)
        val CLOUD_ROOT_KEY = Base64.decode("""
            pAECIAEhWCBLAV5gRgZs+trIjDAIV46A2SB+4llWKqdfQYcDInZoCyJYIEDg+bzxPHLiApaQh4DDXa8k
            R+rvljhZUFNTeYOmk9PE
        """.trimIndent().replace("\n", ""))

        @OptIn(ExperimentalEncodingApi::class)
        val CLOUD_CERT_CHAIN = Base64.decode("""
            g1kBuDCCAbQwggFaoAMCAQICAQEwCgYIKoZIzj0EAwIwLTErMCkGA1UEAwwiQ2xvdWQgU2VjdXJlIEFy
            ZWEgQXR0ZXN0YXRpb24gUm9vdDAiGA8xOTcwMDEwMTAwMDAwMFoYDzIxMDYwMjA3MDYyODE0WjAgMR4w
            HAYDVQQDDBVDbG91ZCBTZWN1cmUgQXJlYSBLZXkwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAStvO4M
            YJB9CVj1JkDk5QcoAZSWD55oFO/0J0mI1DDKUlycyd6BsbzlcSbVnRvS3owNCqQvzGHtYQsqOsZChiVp
            o3QwcjAfBgNVHSMEGDAWgBR7nk6QYpM5u5mKQ3WJJxBejUKJxjAwBgorBgEEAdZ5AgExBCKhaWNoYWxs
            ZW5nZVZhd0x1QjBDNExMMVJnNWxobXJsbVdmMB0GA1UdDgQWBBQwQLxfaQcuzevxaGJ4EmTiKmF5xDAK
            BggqhkjOPQQDAgNIADBFAiBU4piBUIgTePAfxsMPYEt5LdjFPomUBHUJUoE7rrXbqQIhAPnj91IdG3bn
            Zkkd3vjN68hjyiFGARyotSmBfZFxkS1IWQF8MIIBeDCCAR+gAwIBAgIBATAKBggqhkjOPQQDAjAXMRUw
            EwYDVQQDDAxjc2FfZGV2X3Jvb3QwIhgPMjAyNTAxMDEyMDEyNTFaGA8yMDM1MDEwMTIwMTI1MVowLTEr
            MCkGA1UEAwwiQ2xvdWQgU2VjdXJlIEFyZWEgQXR0ZXN0YXRpb24gUm9vdDBZMBMGByqGSM49AgEGCCqG
            SM49AwEHA0IABPNOGesocse2qyi5RwNflue9Psb38emmPdTh1WCN/53gzsNorszVcvdyl/8945l6Ps4i
            snv/DpwwrMMeEKW6pnCjQjBAMB8GA1UdIwQYMBaAFEqc1iDkhWpfhozT8rxG49A6ClfbMB0GA1UdDgQW
            BBR7nk6QYpM5u5mKQ3WJJxBejUKJxjAKBggqhkjOPQQDAgNHADBEAiACl3toPGd0OJ1lk3HUfQFbwZDj
            Z91IiEiCmjL2ruZIqQIgUV65WhS4xvZfRVEWnhU/W5BOJ7OaKlL8iYKXg5fl41ZZAVcwggFTMIH6oAMC
            AQICCQCNcBm6IuEUbzAKBggqhkjOPQQDAjAXMRUwEwYDVQQDDAxjc2FfZGV2X3Jvb3QwHhcNMjQxMTEz
            MDAwNzAzWhcNMzQxMTIxMDAwNzAzWjAXMRUwEwYDVQQDDAxjc2FfZGV2X3Jvb3QwWTATBgcqhkjOPQIB
            BggqhkjOPQMBBwNCAARLAV5gRgZs+trIjDAIV46A2SB+4llWKqdfQYcDInZoC0Dg+bzxPHLiApaQh4DD
            Xa8kR+rvljhZUFNTeYOmk9PEoy8wLTAdBgNVHQ4EFgQUSpzWIOSFal+GjNPyvEbj0DoKV9swDAYDVR0T
            BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiAzvkpHNXojiABGTUgFwJfuf7SvY4RdZ8Xrs3SUdwo2NgIh
            ANCywDVu7SO42f4ZdA2ZzQJWF1IAXYltFz7vWP81gsjj
        """.trimIndent().replace("\n", ""))
    }
}