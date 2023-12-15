package com.android.identity.trustmanagement

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509ExtensionUtils
import org.bouncycastle.cert.bc.BcX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.util.PublicKeyFactory
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date


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

    val caCertificate: X509Certificate
    val intermediateCertificate: X509Certificate
    val dsCertificate: X509Certificate

    init {
        mdlDsCertificate =
            parseCertificate(mdlDsCertificatePem.byteInputStream(Charsets.US_ASCII).readBytes())
        mdlCaCertificate =
            parseCertificate(mdlCaCertificatePem.byteInputStream(Charsets.US_ASCII).readBytes())

        java.security.Security.insertProviderAt(BouncyCastleProvider(), 1)
        val extensionUtils: X509ExtensionUtils = BcX509ExtensionUtils()
        val kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
        kpg.initialize(ECGenParameterSpec("secp256r1"))

        // generate CA certificate
        val keyPairCA = kpg.generateKeyPair()
        val caPublicKeyInfo: SubjectPublicKeyInfo =
            SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(
                PublicKeyFactory.createKey(keyPairCA.public.encoded)
            )
        val nowMillis = System.currentTimeMillis()
        val certBuilderCA = JcaX509v3CertificateBuilder(
            X500Name("CN=Test TrustManager CA"),
            BigInteger.ONE,
            Date(nowMillis),
            Date(nowMillis + 24 * 3600 * 1000),
            X500Name("CN=Test TrustManager CA"),
            keyPairCA.public
        ).addExtension(Extension.basicConstraints, true, BasicConstraints(0))
            .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign))
            .addExtension(
                Extension.subjectKeyIdentifier,
                false,
                extensionUtils.createSubjectKeyIdentifier(caPublicKeyInfo)
            )
        val signerCA = JcaContentSignerBuilder("SHA256withECDSA").build(keyPairCA.private)
        val caHolder = certBuilderCA.build(signerCA)
        val cf = CertificateFactory.getInstance("X.509")
        val caStream = ByteArrayInputStream(caHolder.encoded)
        caCertificate = cf.generateCertificate(caStream) as X509Certificate

        // generate intermediate certificate
        val keyPairIntermediate = kpg.generateKeyPair()
        val intermediatePublicKeyInfo: SubjectPublicKeyInfo =
            SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(
                PublicKeyFactory.createKey(keyPairIntermediate.public.encoded)
            )
        val certBuilderIntermediate = JcaX509v3CertificateBuilder(
            X500Name("CN=Test TrustManager CA"),
            BigInteger.TWO,
            Date(nowMillis),
            Date(nowMillis + 24 * 3600 * 1000),
            X500Name("CN=Test TrustManager Intermediate"),
            keyPairIntermediate.public
        ).addExtension(Extension.basicConstraints, true, BasicConstraints(0))
            .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign))
            .addExtension(
                Extension.authorityKeyIdentifier,
                false,
                extensionUtils.createAuthorityKeyIdentifier(caHolder)
            )
            .addExtension(
                Extension.subjectKeyIdentifier,
                false,
                extensionUtils.createSubjectKeyIdentifier(intermediatePublicKeyInfo)
            )

        val intermediateHolder = certBuilderIntermediate.build(signerCA)
        val intermediateStream = ByteArrayInputStream(intermediateHolder.encoded)
        intermediateCertificate = cf.generateCertificate(intermediateStream) as X509Certificate

        // generate DS certificate
        val keyPairDS = kpg.generateKeyPair()
        val dsPublicKeyInfo: SubjectPublicKeyInfo =
            SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(
                PublicKeyFactory.createKey(keyPairDS.public.encoded)
            )
        val certBuilderDS = JcaX509v3CertificateBuilder(
            X500Name("CN=Test TrustManager Intermediate"),
            BigInteger.ONE.add(BigInteger.TWO),
            Date(nowMillis),
            Date(nowMillis + 24 * 3600 * 1000),
            X500Name("CN=Test TrustManager DS"),
            keyPairDS.public
        ).addExtension(Extension.basicConstraints, true, BasicConstraints(0))
            .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
            .addExtension(
                Extension.authorityKeyIdentifier,
                false,
                extensionUtils.createAuthorityKeyIdentifier(intermediateHolder)
            )
            .addExtension(
                Extension.subjectKeyIdentifier,
                false,
                extensionUtils.createSubjectKeyIdentifier(dsPublicKeyInfo)
            )
        val signerIntermediate =
            JcaContentSignerBuilder("SHA256withECDSA").build(keyPairIntermediate.private)
        val dsHolder = certBuilderDS.build(signerIntermediate)
        val dsStream = ByteArrayInputStream(dsHolder.encoded)
        dsCertificate = cf.generateCertificate(dsStream) as X509Certificate
    }

    @Test
    fun testTrustManagerHappyFlow() {
        // arrange (start with a TrustManager without certificates)
        val trustManager = TrustManager()

        // act (add certificate and verify chain)
        trustManager.addTrustPoint(TrustPoint(mdlCaCertificate))
        val result = trustManager.verify(listOf(mdlDsCertificate))

        // assert
        Assert.assertTrue("DS Certificate is trusted", result.isTrusted)
        Assert.assertEquals("Trust chain contains 2 certificates", 2, result.trustChain.size)
        Assert.assertEquals("Error is empty", result.error, null)
    }

    @Test
    fun testTrustManagerHappyFlowWithIntermediateAndCaCertifcate() {
        // arrange (start with a TrustManager without certificates)
        val trustManager = TrustManager()

        // act (add intermediate and CA certificate and verify chain)
        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))
        trustManager.addTrustPoint(TrustPoint(caCertificate))
        val result = trustManager.verify(listOf(dsCertificate))

        // assert
        Assert.assertTrue("DS Certificate is trusted", result.isTrusted)
        Assert.assertEquals("Trust chain contains 3 certificates", 3, result.trustChain.size)
        Assert.assertEquals("Error is empty", result.error, null)
    }

    @Test
    fun testTrustManagerHappyFlowWithIntermediateCertifcate() {
        // arrange (start with a TrustManager without certificates)
        val trustManager = TrustManager()

        // act (add intermediate certificate (without CA) and verify chain)
        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))
        val result = trustManager.verify(listOf(dsCertificate))

        // assert
        Assert.assertTrue("DS Certificate is trusted", result.isTrusted)
        Assert.assertEquals("Trust chain contains 2 certificates", 2, result.trustChain.size)
        Assert.assertEquals("Error is empty", result.error, null)
    }

    @Test
    fun testTrustManagerCaCertificateMissing() {
        // arrange (start with a TrustManager without certificates)
        val trustManager = TrustManager()

        // act (verify chain)
        val result = trustManager.verify(listOf(mdlDsCertificate))

        // assert
        Assert.assertFalse("DS Certificate is not trusted", result.isTrusted)
        Assert.assertEquals("Trust chain is empty", 0, result.trustChain.size)
        Assert.assertEquals(
            "Trustmanager complains about missing CA Certificate",
            "No trusted root certificate could not be found",
            result.error?.message
        )
    }

    private fun parseCertificate(certificateBytes: ByteArray): X509Certificate {
        return CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
    }
}