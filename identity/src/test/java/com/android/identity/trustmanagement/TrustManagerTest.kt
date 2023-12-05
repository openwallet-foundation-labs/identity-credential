package com.android.identity.trustmanagement

import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class TrustManagerTest {

    val mdlDsCertificatePem = """
        -----BEGIN CERTIFICATE-----
        MIICIzCCAamgAwIBAgIQR29vZ2xlX1Rlc3RfRFNfMTAKBggqhkjOPQQDAzA8MQswCQYDVQQGEwJV
        UzEOMAwGA1UECBMFVVMtTUExHTAbBgNVBAMMFEdvb2dsZSBURVNUIElBQ0EgbURMMB4XDTIzMDcy
        NjAwMDAwMVoXDTI0MTAyNTAwMDAwMVowOjELMAkGA1UEBhMCVVMxDjAMBgNVBAgTBVVTLU1BMRsw
        GQYDVQQDDBJHb29nbGUgVEVTVCBEUyBtREwwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQsgMEL
        9w9jvdzEHqINdqIuy6Kpf6iBG/GdVyQzsSwMHz+ZTAQ75+F90IOHKBusDDelKTYbPLNqD6w41BrA
        ZvkDo4GOMIGLMB8GA1UdIwQYMBaAFN7zq2033p46gW4QMtArSK81inGrMB0GA1UdDgQWBBQ5e+U8
        UN/w16LkqeRl6nN6ntiSyDAOBgNVHQ8BAf8EBAMCB4AwIgYDVR0SBBswGYYXaHR0cHM6Ly93d3cu
        Z29vZ2xlLmNvbS8wFQYDVR0lAQH/BAswCQYHKIGMXQUBAjAKBggqhkjOPQQDAwNoADBlAjEAolzb
        im8MI5BbnT9YKmCPDBb0NrXu41h9LKl3FnxvFqK53hD+Hd0W3gcuT4ZvBgWnAjBqSIlCH5uzdLi9
        4XWz1qsjIWupfmiVEJdK4PZZoQ6VhGm1vYDfwQWIgx2TGYGmOZg=
        -----END CERTIFICATE-----
    """.trimIndent()

    val mdlCaCertificatePem = """
        -----BEGIN CERTIFICATE-----
        MIICGzCCAaGgAwIBAgIQR29vZ2xlX1Rlc3RfQ0FfMjAKBggqhkjOPQQDAzA8MQswCQYDVQQGEwJV
        UzEOMAwGA1UECBMFVVMtTUExHTAbBgNVBAMMFEdvb2dsZSBURVNUIElBQ0EgbURMMB4XDTIzMDcy
        NTAwMDAwMFoXDTMyMDcyNTAwMDAwMFowPDELMAkGA1UEBhMCVVMxDjAMBgNVBAgTBVVTLU1BMR0w
        GwYDVQQDDBRHb29nbGUgVEVTVCBJQUNBIG1ETDB2MBAGByqGSM49AgEGBSuBBAAiA2IABJ30KbCI
        WLZlJSMRzNBhcTgGa2/d39UVhZ6sKh8G5LAZUsYbGSmKBNuHWe3s2XCs566p+1pkkjKaxByq+KtM
        fiC1Gi21k77JjjcY/G0a62DsciAxVOtrNLQlv/KHPTePjqNoMGYwHQYDVR0OBBYEFN7zq2033p46
        gW4QMtArSK81inGrMA4GA1UdDwEB/wQEAwIBBjAhBgNVHRIEGjAYhhZodHRwczovL3d3dy5nb29n
        bGUuY29tMBIGA1UdEwEB/wQIMAYBAQECAQAwCgYIKoZIzj0EAwMDaAAwZQIxAJaqxSfxFhOBx+OS
        lCdG+dVipQN6t3OKYLb9G5O86GBaNVkuZp4L5dcvrOFLbEggjAIwKKbF1keoCaZsUXmwJolWDnYz
        nH5NbLz9MgAhNPxc99c+z1XNn5PhsOBn6CiFybHc
        -----END CERTIFICATE-----
    """.trimIndent()

    val mdlDsCertificate: X509Certificate
    val mdlCaCertificate: X509Certificate

    init {
        mdlDsCertificate =
            parseCertificate(mdlDsCertificatePem.byteInputStream(Charsets.US_ASCII).readBytes())
        mdlCaCertificate =
            parseCertificate(mdlCaCertificatePem.byteInputStream(Charsets.US_ASCII).readBytes())
    }

    @Test
    fun testTrustManagerHappyFlow() {
        // arrange (start with a TrustManager without certificates)
        val trustManager = TrustManager()

        // act (add certificate and verify chain)
        trustManager.addCertificate(mdlCaCertificate)
        val result = trustManager.verify(listOf(mdlDsCertificate))

        // assert
        Assert.assertTrue("DS Certificate is trusted", result.isTrusted)
        Assert.assertEquals("Trust chain contains 2 certificates",2, result.trustChain.size)
        Assert.assertEquals("Error is empty", result.error, null)
    }

    @Test
    fun testTrustManagerHappyCaCertificateMissing() {
        // arrange (start with a TrustManager without certificates)
        val trustManager = TrustManager()

        // act (verify chain)
        val result = trustManager.verify(listOf(mdlDsCertificate))

        // assert
        Assert.assertFalse("DS Certificate is not trusted", result.isTrusted)
        Assert.assertEquals("Trust chain is empty",0, result.trustChain.size)
        Assert.assertEquals("Trustmanager complains about missing CA Certificate", result.error?.message, "Trusted root certificate could not be found")
    }
    private fun parseCertificate(certificateBytes: ByteArray): X509Certificate {
        return CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
    }
}