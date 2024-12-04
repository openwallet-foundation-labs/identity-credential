package com.android.identity.crypto

import com.android.identity.asn1.ASN1
import com.android.identity.asn1.ASN1Integer
import com.android.identity.asn1.ASN1OctetString
import com.android.identity.util.toHex
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import org.bouncycastle.asn1.x500.X500Name as bcX500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jcajce.spec.XDHParameterSpec
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class X509CertTestsJvm {

    @BeforeTest
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    // This is Maryland's IACA certificate (IACA_Root_2024.cer) downloaded from
    //
    //  https://mva.maryland.gov/Pages/MDMobileID_Googlewallet.aspx
    //
    private val exampleX509Cert = X509Cert.fromPem(
        """
-----BEGIN CERTIFICATE-----
MIICxjCCAmygAwIBAgITJkV7El8K11IXqY7mz96n/EhiITAKBggqhkjOPQQDAjBq
MQ4wDAYDVQQIEwVVUy1NRDELMAkGA1UEBhMCVVMxFDASBgNVBAcTC0dsZW4gQnVy
bmllMRUwEwYDVQQKEwxNYXJ5bGFuZCBNVkExHjAcBgNVBAMTFUZhc3QgRW50ZXJw
cmlzZXMgUm9vdDAeFw0yNDAxMDUwNTAwMDBaFw0yOTAxMDQwNTAwMDBaMGoxDjAM
BgNVBAgTBVVTLU1EMQswCQYDVQQGEwJVUzEUMBIGA1UEBxMLR2xlbiBCdXJuaWUx
FTATBgNVBAoTDE1hcnlsYW5kIE1WQTEeMBwGA1UEAxMVRmFzdCBFbnRlcnByaXNl
cyBSb290MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaWcKIqlAWboV93RAa5ad
0LJBn8W0/yYwtOyUlxuTxoo4SPkorKmOz3EhThC+U4WRrt13aSnCsJtK+waBFghX
u6OB8DCB7TAOBgNVHQ8BAf8EBAMCAQYwEgYDVR0TAQH/BAgwBgEB/wIBADAdBgNV
HQ4EFgQUTprRzaFBJ1SLjJsO01tlLCQ4YF0wPAYDVR0SBDUwM4EWbXZhY3NAbWRv
dC5zdGF0ZS5tZC51c4YZaHR0cHM6Ly9tdmEubWFyeWxhbmQuZ292LzBYBgNVHR8E
UTBPME2gS6BJhkdodHRwczovL215bXZhLm1hcnlsYW5kLmdvdjo1NDQzL01EUC9X
ZWJTZXJ2aWNlcy9DUkwvbURML3Jldm9jYXRpb25zLmNybDAQBgkrBgEEAYPFIQEE
A01EUDAKBggqhkjOPQQDAgNIADBFAiEAnX3+E4E5dQ+5G1rmStJTW79ZAiDTabyL
8lJuYL/nDxMCIHHkAyIJcQlQmKDUVkBr3heUd5N9Y8GWdbWnbHuwe7Om
-----END CERTIFICATE-----
        """.trimIndent())

    @Test
    fun testX509Parsing() {
        val cert = exampleX509Cert
        val javaCert = exampleX509Cert.javaX509Certificate

        // This checks that out own ASN.1 / X.509 routines agree with the ones in Java.
        //

        assertEquals(cert.subject.name, javaCert.subjectX500Principal.name)
        assertEquals(cert.issuer.name, javaCert.issuerX500Principal.name)
        assertEquals(cert.version, javaCert.version - 1)
        assertEquals(cert.serialNumber, ASN1Integer(javaCert.serialNumber.toByteArray()))
        assertEquals(cert.validityNotBefore, javaCert.notBefore.toInstant().toKotlinInstant())
        assertEquals(cert.validityNotAfter, javaCert.notAfter.toInstant().toKotlinInstant())

        assertContentEquals(cert.tbsCertificate, javaCert.tbsCertificate)
        assertContentEquals(cert.signature, javaCert.signature)
        assertEquals(cert.signatureAlgorithm, Algorithm.ES256)

        assertEquals(cert.criticalExtensionOIDs, javaCert.criticalExtensionOIDs)
        assertEquals(cert.nonCriticalExtensionOIDs, javaCert.nonCriticalExtensionOIDs)

        for (oid in cert.criticalExtensionOIDs + cert.nonCriticalExtensionOIDs) {
            // Note: Java API always return the value wrapped in an OCTET STRING. So we need to unwrap it.
            val ourValue = cert.getExtensionValue(oid)
            val javaValue = javaCert.getExtensionValue(oid)
            val javaValueUnwrapped = (ASN1.decode(javaValue) as ASN1OctetString).value
            assertContentEquals(ourValue, javaValueUnwrapped)
        }
        // Check non-existent extensions (1.2.3.4.5 as an example) return null
        assertNull(cert.getExtensionValue("1.2.3.4.5"))
        assertNull(javaCert.getExtensionValue("1.2.3.4.5"))
        assertEquals("4e9ad1cda14127548b8c9b0ed35b652c2438605d", cert.subjectKeyIdentifier!!.toHex())
    }

    // Checks that the Java X509Certificate.verify() works with certificates created by X509Cert.Builder
    private fun testJavaCertSignedWithCurve(curve: EcCurve) {
        val key = Crypto.createEcPrivateKey(curve)
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val cert = X509Cert.Builder(
            publicKey = key.publicKey,
            signingKey = key,
            signatureAlgorithm = key.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Foobar"),
            issuer = X500Name.fromName("CN=Foobar"),
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        ).build()
        println("blah_cert\n:${cert.toPem()}")
        val javaCert = cert.javaX509Certificate
        javaCert.verify(key.publicKey.javaPublicKey)
    }

    @Test fun testCertSignedWithCurve_P256() = testJavaCertSignedWithCurve(EcCurve.P256)
    @Test fun testCertSignedWithCurve_P384() = testJavaCertSignedWithCurve(EcCurve.P384)
    @Test fun testCertSignedWithCurve_P521() = testJavaCertSignedWithCurve(EcCurve.P521)
    @Test fun testCertSignedWithCurve_BRAINPOOLP256R1() = testJavaCertSignedWithCurve(EcCurve.BRAINPOOLP256R1)
    @Test fun testCertSignedWithCurve_BRAINPOOLP320R1() = testJavaCertSignedWithCurve(EcCurve.BRAINPOOLP320R1)
    @Test fun testCertSignedWithCurve_BRAINPOOLP384R1() = testJavaCertSignedWithCurve(EcCurve.BRAINPOOLP384R1)
    @Test fun testCertSignedWithCurve_BRAINPOOLP512R1() = testJavaCertSignedWithCurve(EcCurve.BRAINPOOLP512R1)
    @Test fun testCertSignedWithCurve_ED25519() = testJavaCertSignedWithCurve(EcCurve.ED25519)
    @Test fun testCertSignedWithCurve_ED448() = testJavaCertSignedWithCurve(EcCurve.ED448)

}