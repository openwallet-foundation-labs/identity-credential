package com.android.mdl.appreader.issuerauth.vical

import org.bouncycastle.util.io.pem.PemReader
import org.junit.Before
import java.io.StringReader
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.LinkedList

object TestCertificates {
    private val ROOT_CERT = """
            -----BEGIN CERTIFICATE-----
            MIICFDCCAZqgAwIBAgIQR29vZ2xlX1Rlc3RfQ0FfMTAKBggqhkjOPQQDAzAsMQsw
            CQYDVQQGEwJVVDEdMBsGA1UEAwwUR29vZ2xlIFRFU1QgSUFDQSBtREwwHhcNMjEw
            OTA2MjIwMDAwWhcNMzAwOTA2MjIwMDAwWjAsMQswCQYDVQQGEwJVVDEdMBsGA1UE
            AwwUR29vZ2xlIFRFU1QgSUFDQSBtREwwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAAQs
            0ORGkl5mPyY7nX6xftZJiQQSU7ZuDPn4XVDiSsOE1C0tb5WrOfw0gCn8DcuSGj1k
            z+PT504KE6MUfHkWL/uxrIoS4uWx/8YeNxjQ2Pqjmx+Cn6qYoZD+LPXRa3tSBOej
            gYAwfjAdBgNVHQ4EFgQUyTfhPTUT0ax4qI2uSqnTT5E0FSAwDgYDVR0PAQH/BAQD
            AgEGMCIGA1UdEgQbMBmGF2h0dHBzOi8vd3d3Lmdvb2dsZS5jb20vMBIGA1UdEwEB
            /wQIMAYBAf8CAQAwFQYDVR0lAQH/BAswCQYHKIGMXQUBBzAKBggqhkjOPQQDAwNo
            ADBlAjA0rss7V3XI8vSKJ3IDNFgg10xHhty6zjf8aVcrObwVDIJYeMeNuNytOlh1
            AQSd4YcCMQCMDQ3VXNGK0pc8Hf606qCWZyrLthfOVixLv+fDCE5KERetZhx46uXs
            oGsskbv+YNc=
            -----END CERTIFICATE-----
        """.trimIndent()

    private val SIGNING_CERT = """
        -----BEGIN CERTIFICATE-----
        MIICCTCCAY+gAwIBAgIQVUxfVEVTVF9WSUNBTF9fMTAKBggqhkjOPQQDAzAvMRMw
        EQYDVQQDDApVTCBURVNUIENBMQswCQYDVQQKDAJVTDELMAkGA1UEBhMCVVQwHhcN
        MjEwOTA2MjIwMDAwWhcNMjIxMjA2MjMwMDAwWjAxMRUwEwYDVQQDDAxVTCBURVNU
        IFNpZ24xCzAJBgNVBAoMAlVMMQswCQYDVQQGEwJVVDBZMBMGByqGSM49AgEGCCqG
        SM49AwEHA0IABHqm2mJeHt5q4+/CcgjI7UTEXag5bQmB23/omzHrKH8xqlM2GkuZ
        gMVJTaokBessGlDAQtMS+9Qjt5INUVHmC8WjgYowgYcwHwYDVR0jBBgwFoAUkFnr
        b/VcdRiVQJvFu+O7pgAkKHQwHQYDVR0OBBYEFBkgyw1Q2n5iAMQ5BOwd88gogVnN
        MA4GA1UdDwEB/wQEAwIGQDAeBgNVHRIEFzAVhhNodHRwczovL3d3dy51bC5jb20v
        MBUGA1UdJQEB/wQLMAkGByiBjF0FAQgwCgYIKoZIzj0EAwMDaAAwZQIxAOVCTozL
        NXeC33klZpJmelUYSUgLTByNa9IXAvIR+n9GHKARzjj2k6ASBGjaE7jt3wIwXkm7
        VeQdZmV+n2bfZ4w6tUBUzkYGG5Z7dj6bYNKM6lM3aD2NUFrNU0yiKVuj8s1F
        -----END CERTIFICATE-----
    """.trimIndent()

    private val SIGNING_KEY = """
        -----BEGIN PRIVATE KEY-----
        MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCCbXhK4NLek0/8Izqd8
        qHus41PBw0KdYE/SbJuuRybTCA==
        -----END PRIVATE KEY-----
    """.trimIndent()

    val iacaCerts: MutableList<X509Certificate> = LinkedList()
    var signingCert: X509Certificate? = null
    var signingKey: PrivateKey? = null

//    @Before
//    @Throws(Exception::class)
//    fun loadSigningCertificate() {
//        val certFact = CertificateFactory.getInstance("X509")
//        val certStream =
//            this.javaClass.classLoader.getResourceAsStream("com/ul/vical/test/UL_VICAL_Sign.cer")
//        val cert = certFact.generateCertificate(certStream) as X509Certificate
//        signingCert = cert
//    }

    fun readSigningKey(): PrivateKey {
        val pemReader = PemReader(StringReader(SIGNING_KEY))
        val pemObject = pemReader.readPemObject ();
        val ecdsa = KeyFactory.getInstance ("EC");
        return ecdsa.generatePrivate(PKCS8EncodedKeySpec(pemObject.getContent()));
    }

    init {
        val certFact = CertificateFactory.getInstance("X509")
        // TODO() // add resources first, check if this resource loading should be performed differently
        // val certStream = resources.openRawResource("raw/google_mdl_iaca_cert.pem")
        // this.javaClass.classLoader.getResourceAsStream("raw/google_mdl_iaca_cert.pem")
        val iacaCert = certFact.generateCertificate(ROOT_CERT.byteInputStream(Charsets.US_ASCII)) as X509Certificate
        iacaCerts.add(iacaCert)

        signingCert = certFact.generateCertificate(SIGNING_CERT.byteInputStream(Charsets.US_ASCII)) as X509Certificate
        signingKey = readSigningKey()
    }
}