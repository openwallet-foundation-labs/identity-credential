package org.multipaz.trustmanagement

import kotlinx.coroutines.test.runTest
import org.multipaz.asn1.ASN1Integer
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509KeyUsage
import kotlinx.datetime.Clock
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.buildX509Cert
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.mdoc.vical.Vical
import org.multipaz.mdoc.vical.VicalCertificateInfo
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.toHex
import org.multipaz.util.truncateToWholeSeconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


class TrustManagerTest {

    val caCertificate: X509Cert
    val intermediateCertificate: X509Cert
    val dsCertificate: X509Cert

    val dsValidInThePastCertificate: X509Cert
    val dsValidInTheFutureCertificate: X509Cert

    val ca2Certificate: X509Cert
    val ds2Certificate: X509Cert

    init {
        val now = Clock.System.now().truncateToWholeSeconds()

        val caKey = Crypto.createEcPrivateKey(EcCurve.P384)
        caCertificate = buildX509Cert(
            publicKey = caKey.publicKey,
            signingKey = caKey,
            signatureAlgorithm = caKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager CA"),
            issuer = X500Name.fromName("CN=Test TrustManager CA"),
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        ) {
            includeSubjectKeyIdentifier()
            setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
        }

        val intermediateKey = Crypto.createEcPrivateKey(EcCurve.P384)
        intermediateCertificate = buildX509Cert(
            publicKey = intermediateKey.publicKey,
            signingKey = caKey,
            signatureAlgorithm = caKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager Intermediate CA"),
            issuer = caCertificate.subject,
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        ) {
            includeSubjectKeyIdentifier()
            setAuthorityKeyIdentifierToCertificate(caCertificate)
            setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
        }

        val dsKey = Crypto.createEcPrivateKey(EcCurve.P384)
        dsCertificate = buildX509Cert(
            publicKey = dsKey.publicKey,
            signingKey = intermediateKey,
            signatureAlgorithm = intermediateKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager DS"),
            issuer = intermediateCertificate.subject,
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        ) {
            includeSubjectKeyIdentifier()
            setAuthorityKeyIdentifierToCertificate(intermediateCertificate)
            setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
        }

        val dsValidInThePastKey = Crypto.createEcPrivateKey(EcCurve.P384)
        dsValidInThePastCertificate = buildX509Cert(
            publicKey = dsValidInThePastKey.publicKey,
            signingKey = intermediateKey,
            signatureAlgorithm = intermediateKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager DS Valid In The Past"),
            issuer = intermediateCertificate.subject,
            validFrom = now - 3.hours,
            validUntil = now - 1.hours
        ) {
            includeSubjectKeyIdentifier()
            setAuthorityKeyIdentifierToCertificate(intermediateCertificate)
            setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
        }

        val dsValidInTheFutureKey = Crypto.createEcPrivateKey(EcCurve.P384)
        dsValidInTheFutureCertificate = buildX509Cert(
            publicKey = dsValidInTheFutureKey.publicKey,
            signingKey = intermediateKey,
            signatureAlgorithm = intermediateKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager Valid In The Future"),
            issuer = intermediateCertificate.subject,
            validFrom = now + 1.hours,
            validUntil = now + 3.hours
        ) {
            includeSubjectKeyIdentifier()
            setAuthorityKeyIdentifierToCertificate(intermediateCertificate)
            setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
        }

        val ca2Key = Crypto.createEcPrivateKey(EcCurve.P384)
        ca2Certificate = buildX509Cert(
            publicKey = ca2Key.publicKey,
            signingKey = ca2Key,
            signatureAlgorithm = ca2Key.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager CA2"),
            issuer = X500Name.fromName("CN=Test TrustManager CA2"),
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        ) {
            includeSubjectKeyIdentifier()
            setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
        }

        val ds2Key = Crypto.createEcPrivateKey(EcCurve.P384)
        ds2Certificate = buildX509Cert(
            publicKey = ds2Key.publicKey,
            signingKey = ca2Key,
            signatureAlgorithm = ca2Key.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager DS2"),
            issuer = ca2Certificate.subject,
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        ) {
            includeSubjectKeyIdentifier()
            setAuthorityKeyIdentifierToCertificate(ca2Certificate)
            setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
        }
    }

    @Test
    fun happyFlow() = runTest {
        val trustManager = LocalTrustManager(EphemeralStorage())

        trustManager.addTrustPoint(intermediateCertificate, TrustPointMetadata())
        trustManager.addTrustPoint(caCertificate, TrustPointMetadata())

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun validInThePast() = runTest {
        val trustManager = LocalTrustManager(EphemeralStorage())

        trustManager.addTrustPoint(intermediateCertificate, TrustPointMetadata())
        trustManager.addTrustPoint(caCertificate, TrustPointMetadata())

        trustManager.verify(listOf(dsValidInThePastCertificate)).let {
            assertTrue(it.error!!.message!!.startsWith("Certificate is no longer valid"))
            assertFalse(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun validInTheFuture() = runTest {
        val trustManager = LocalTrustManager(EphemeralStorage())

        trustManager.addTrustPoint(intermediateCertificate, TrustPointMetadata())
        trustManager.addTrustPoint(caCertificate, TrustPointMetadata())

        trustManager.verify(listOf(dsValidInTheFutureCertificate)).let {
            assertTrue(it.error!!.message!!.startsWith("Certificate is not yet valid"))
            assertFalse(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun happyFlowWithOnlyIntermediateCertificate() = runTest {
        val trustManager = LocalTrustManager(EphemeralStorage())

        trustManager.addTrustPoint(intermediateCertificate, TrustPointMetadata())

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(2, it.trustChain!!.certificates.size)
            assertEquals(intermediateCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun happyFlowWithChainOfTwo() = runTest {
        val trustManager = LocalTrustManager(EphemeralStorage())

        trustManager.addTrustPoint(caCertificate, TrustPointMetadata())

        trustManager.verify(listOf(dsCertificate, intermediateCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun trustPointNotCaCert() = runTest {
        val trustManager = LocalTrustManager(EphemeralStorage())

        trustManager.addTrustPoint(dsCertificate, TrustPointMetadata())

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(1, it.trustChain!!.certificates.size)
            assertEquals(dsCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun happyFlowMultipleCerts() = runTest {
        val trustManager = LocalTrustManager(EphemeralStorage())

        trustManager.addTrustPoint(intermediateCertificate, TrustPointMetadata())
        trustManager.addTrustPoint(caCertificate, TrustPointMetadata())
        trustManager.addTrustPoint(ca2Certificate, TrustPointMetadata())

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }

        trustManager.verify(listOf(ds2Certificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(2, it.trustChain!!.certificates.size)
            assertEquals(ca2Certificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun happyFlowOrigin() = runTest {
        val trustManager = LocalTrustManager(EphemeralStorage())

        val originTrustPoint = trustManager.addTrustPoint("https://verifier.multipaz.org", TrustPointMetadata())

        trustManager.verify("https://verifier.multipaz.org").let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertNull(it.trustChain)
            assertEquals(1, it.trustPoints.size)
            assertEquals(originTrustPoint, it.trustPoints[0])
        }

        trustManager.verify("https://verifier2.multipaz.org").let {
            assertEquals("No trusted origin could not be found", it.error?.message)
            assertFalse(it.isTrusted)
            assertNull(it.trustChain)
            assertEquals(0, it.trustPoints.size)
        }
    }

    @Test
    fun noTrustPoints() = runTest {
        val trustManager = LocalTrustManager(EphemeralStorage())

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals("No trusted root certificate could not be found", it.error?.message)
            assertFalse(it.isTrusted)
            assertNull(it.trustChain)
            assertEquals(0, it.trustPoints.size)
        }

        trustManager.verify("https://verifier.multipaz.org").let {
            assertEquals("No trusted origin could not be found", it.error?.message)
            assertFalse(it.isTrusted)
            assertNull(it.trustChain)
            assertEquals(0, it.trustPoints.size)
        }
    }

    @Test
    fun skiAlreadyExists() = runTest {
        val storage = EphemeralStorage()
        val trustManager = LocalTrustManager(storage)

        trustManager.addTrustPoint(intermediateCertificate, TrustPointMetadata())
        trustManager.addTrustPoint(caCertificate, TrustPointMetadata())
        val e = assertFailsWith(TrustPointAlreadyExistsException::class) {
            trustManager.addTrustPoint(caCertificate, TrustPointMetadata())
        }
        assertEquals("TrustPoint with given SubjectKeyIdentifier already exists", e.message)
    }

    @Test
    fun originAlreadyExists() = runTest {
        val storage = EphemeralStorage()
        val trustManager = LocalTrustManager(storage)
        trustManager.addTrustPoint("https://verifier.multipaz.org", TrustPointMetadata())

        val e = assertFailsWith(TrustPointAlreadyExistsException::class) {
            trustManager.addTrustPoint("https://verifier.multipaz.org", TrustPointMetadata())
        }
        assertEquals("TrustPoint with given origin already exists", e.message)
    }

    @Test
    fun persistence() = runTest {
        val storage = EphemeralStorage()
        val trustManager = LocalTrustManager(storage)

        trustManager.addTrustPoint(intermediateCertificate, TrustPointMetadata())
        trustManager.addTrustPoint(caCertificate, TrustPointMetadata())
        val ca2TrustPoint = trustManager.addTrustPoint(ca2Certificate, TrustPointMetadata())
        assertEquals(setOf(
            "x509-ski:" + caCertificate.subjectKeyIdentifier!!.toHex(),
            "x509-ski:" + ca2Certificate.subjectKeyIdentifier!!.toHex(),
            "x509-ski:" + intermediateCertificate.subjectKeyIdentifier!!.toHex(),
        ), trustManager.getTrustPoints().map { it.identifier }.toSet())

        val originTrustPoint = trustManager.addTrustPoint("https://verifier.multipaz.org", TrustPointMetadata())
        assertEquals(setOf(
            "x509-ski:" + caCertificate.subjectKeyIdentifier!!.toHex(),
            "x509-ski:" + ca2Certificate.subjectKeyIdentifier!!.toHex(),
            "x509-ski:" + intermediateCertificate.subjectKeyIdentifier!!.toHex(),
            "origin:https://verifier.multipaz.org"
        ), trustManager.getTrustPoints().map { it.identifier }.toSet())

        trustManager.addTrustPoint("https://verifier2.multipaz.org", TrustPointMetadata())
        assertEquals(setOf(
            "x509-ski:" + caCertificate.subjectKeyIdentifier!!.toHex(),
            "x509-ski:" + ca2Certificate.subjectKeyIdentifier!!.toHex(),
            "x509-ski:" + intermediateCertificate.subjectKeyIdentifier!!.toHex(),
            "origin:https://verifier.multipaz.org",
            "origin:https://verifier2.multipaz.org"
        ), trustManager.getTrustPoints().map { it.identifier }.toSet())

        assertTrue(trustManager.deleteTrustPoint(ca2TrustPoint))
        assertEquals(setOf(
            "x509-ski:" + caCertificate.subjectKeyIdentifier!!.toHex(),
            "x509-ski:" + intermediateCertificate.subjectKeyIdentifier!!.toHex(),
            "origin:https://verifier.multipaz.org",
            "origin:https://verifier2.multipaz.org"
        ), trustManager.getTrustPoints().map { it.identifier }.toSet())
        assertFalse(trustManager.deleteTrustPoint(ca2TrustPoint))

        assertTrue(trustManager.deleteTrustPoint(originTrustPoint))
        assertEquals(setOf(
            "x509-ski:" + caCertificate.subjectKeyIdentifier!!.toHex(),
            "x509-ski:" + intermediateCertificate.subjectKeyIdentifier!!.toHex(),
            "origin:https://verifier2.multipaz.org"
        ), trustManager.getTrustPoints().map { it.identifier }.toSet())
        assertFalse(trustManager.deleteTrustPoint(originTrustPoint))

        val otherTrustManager = LocalTrustManager(storage)
        assertEquals(setOf(
            "x509-ski:" + caCertificate.subjectKeyIdentifier!!.toHex(),
            "x509-ski:" + intermediateCertificate.subjectKeyIdentifier!!.toHex(),
            "origin:https://verifier2.multipaz.org"
        ), otherTrustManager.getTrustPoints().map { it.identifier }.toSet())

        val otherStorage = EphemeralStorage()
        val yetAnotherTrustManager = LocalTrustManager(otherStorage)
        assertEquals(emptySet(), yetAnotherTrustManager.getTrustPoints().map { it.identifier }.toSet())
    }

    @Test
    fun testVicalTrustManager() = runTest {
        val now = Clock.System.now()
        val validFrom = now - 10.minutes
        val validUntil = now + 10.minutes

        val vicalKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val vicalCert = buildX509Cert(
            publicKey = vicalKey.publicKey,
            signingKey = vicalKey,
            signatureAlgorithm = vicalKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1),
            subject = X500Name.fromName("CN=Test VICAL provider"),
            issuer = X500Name.fromName("CN=Test VICAL provider"),
            validFrom = validFrom,
            validUntil = validUntil
        ) {
            includeSubjectKeyIdentifier()
        }

        val vical = Vical(
            version = "1",
            vicalProvider = "Test VICAL provider",
            date = now,
            nextUpdate = null,
            vicalIssueID = null,
            certificateInfos = listOf(
                VicalCertificateInfo(
                    certificate = caCertificate,
                    docTypes = listOf("org.iso.18013.5.1.mDL")
                ),
                VicalCertificateInfo(
                    certificate = ca2Certificate,
                    docTypes = listOf("org.iso.18013.5.1.mDL")
                )
            )
        )
        val signedVical = SignedVical(vical, X509CertChain(listOf(vicalCert)))
        val trustManager = VicalTrustManager(signedVical)

        // Happy flow
        //
        trustManager.verify(listOf(dsCertificate, intermediateCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }
        trustManager.verify(listOf(ds2Certificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(2, it.trustChain!!.certificates.size)
            assertEquals(ca2Certificate, it.trustChain.certificates.last())
        }

        // Valid in the past
        //
        trustManager.verify(listOf(dsValidInThePastCertificate, intermediateCertificate)).let {
            assertTrue(it.error!!.message!!.startsWith("Certificate is no longer valid"))
            assertFalse(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }

        // Valid in the future
        //
        trustManager.verify(listOf(dsValidInTheFutureCertificate, intermediateCertificate)).let {
            assertTrue(it.error!!.message!!.startsWith("Certificate is not yet valid"))
            assertFalse(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }

        // No trust point
        //
        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals("No trusted root certificate could not be found", it.error?.message)
            assertFalse(it.isTrusted)
            assertNull(it.trustChain)
            assertEquals(0, it.trustPoints.size)
        }
    }

    @Test
    fun testCompositeTrustManager() = runTest {
        val tm1 = LocalTrustManager(EphemeralStorage()).apply { addTrustPoint(caCertificate, TrustPointMetadata()) }
        val tm2 = LocalTrustManager(EphemeralStorage()).apply { addTrustPoint(ca2Certificate, TrustPointMetadata()) }
        val trustManager = CompositeTrustManager(listOf(tm1, tm2))

        // Happy flow
        //
        trustManager.verify(listOf(dsCertificate, intermediateCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
            assertEquals(1, it.trustPoints.size)
            assertEquals(it.trustPoints[0].trustManager, tm1)
        }
        trustManager.verify(listOf(ds2Certificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(2, it.trustChain!!.certificates.size)
            assertEquals(ca2Certificate, it.trustChain.certificates.last())
            assertEquals(1, it.trustPoints.size)
            assertEquals(it.trustPoints[0].trustManager, tm2)
        }

        // No trust point
        //
        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals("No trusted root certificate could not be found", it.error?.message)
            assertFalse(it.isTrusted)
            assertNull(it.trustChain)
            assertEquals(0, it.trustPoints.size)
        }
    }
}