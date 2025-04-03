package org.multipaz.trustmanagement

import kotlinx.coroutines.test.runTest
import org.multipaz.asn1.ASN1Integer
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509KeyUsage
import kotlinx.datetime.Clock
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours


class LocalTrustManagerTest {

    val caCertificate: X509Cert
    val intermediateCertificate: X509Cert
    val dsCertificate: X509Cert

    val dsValidInThePastCertificate: X509Cert
    val dsValidInTheFutureCertificate: X509Cert

    val ca2Certificate: X509Cert
    val ds2Certificate: X509Cert

    init {
        val now = Clock.System.now()

        val caKey = Crypto.createEcPrivateKey(EcCurve.P384)
        caCertificate =
            X509Cert.Builder(
                publicKey = caKey.publicKey,
                signingKey = caKey,
                signatureAlgorithm = caKey.curve.defaultSigningAlgorithm,
                serialNumber = ASN1Integer(1L),
                subject = X500Name.fromName("CN=Test TrustManager CA"),
                issuer = X500Name.fromName("CN=Test TrustManager CA"),
                validFrom = now - 1.hours,
                validUntil = now + 1.hours
            )
                .includeSubjectKeyIdentifier()
                .setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
                .build()

        val intermediateKey = Crypto.createEcPrivateKey(EcCurve.P384)
        intermediateCertificate = X509Cert.Builder(
            publicKey = intermediateKey.publicKey,
            signingKey = caKey,
            signatureAlgorithm = caKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager Intermediate CA"),
            issuer = caCertificate.subject,
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(caCertificate)
            .setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
            .build()

        val dsKey = Crypto.createEcPrivateKey(EcCurve.P384)
        dsCertificate = X509Cert.Builder(
            publicKey = dsKey.publicKey,
            signingKey = intermediateKey,
            signatureAlgorithm = intermediateKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager DS"),
            issuer = intermediateCertificate.subject,
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(intermediateCertificate)
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()

        val dsValidInThePastKey = Crypto.createEcPrivateKey(EcCurve.P384)
        dsValidInThePastCertificate = X509Cert.Builder(
            publicKey = dsValidInThePastKey.publicKey,
            signingKey = intermediateKey,
            signatureAlgorithm = intermediateKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager DS Valid In The Past"),
            issuer = intermediateCertificate.subject,
            validFrom = now - 3.hours,
            validUntil = now - 1.hours
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(intermediateCertificate)
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()

        val dsValidInTheFutureKey = Crypto.createEcPrivateKey(EcCurve.P384)
        dsValidInTheFutureCertificate = X509Cert.Builder(
            publicKey = dsValidInTheFutureKey.publicKey,
            signingKey = intermediateKey,
            signatureAlgorithm = intermediateKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager Valid In The Future"),
            issuer = intermediateCertificate.subject,
            validFrom = now + 1.hours,
            validUntil = now + 3.hours
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(intermediateCertificate)
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()

        val ca2Key = Crypto.createEcPrivateKey(EcCurve.P384)
        ca2Certificate =
            X509Cert.Builder(
                publicKey = ca2Key.publicKey,
                signingKey = ca2Key,
                signatureAlgorithm = ca2Key.curve.defaultSigningAlgorithm,
                serialNumber = ASN1Integer(1L),
                subject = X500Name.fromName("CN=Test TrustManager CA2"),
                issuer = X500Name.fromName("CN=Test TrustManager CA2"),
                validFrom = now - 1.hours,
                validUntil = now + 1.hours
            )
                .includeSubjectKeyIdentifier()
                .setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
                .build()

        val ds2Key = Crypto.createEcPrivateKey(EcCurve.P384)
        ds2Certificate = X509Cert.Builder(
            publicKey = ds2Key.publicKey,
            signingKey = ca2Key,
            signatureAlgorithm = ca2Key.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager DS2"),
            issuer = ca2Certificate.subject,
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(ca2Certificate)
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()
    }

    @Test
    fun happyFlow() = runTest {
        val trustManager = LocalTrustManager.create(EphemeralStorage())

        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))
        trustManager.addTrustPoint(TrustPoint(caCertificate))

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun validInThePast() = runTest {
        val trustManager = LocalTrustManager.create(EphemeralStorage())

        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))
        trustManager.addTrustPoint(TrustPoint(caCertificate))

        trustManager.verify(listOf(dsValidInThePastCertificate)).let {
            assertTrue(it.error!!.message!!.startsWith("Certificate is no longer valid"))
            assertFalse(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun validInTheFuture() = runTest {
        val trustManager = LocalTrustManager.create(EphemeralStorage())

        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))
        trustManager.addTrustPoint(TrustPoint(caCertificate))

        trustManager.verify(listOf(dsValidInTheFutureCertificate)).let {
            assertTrue(it.error!!.message!!.startsWith("Certificate is not yet valid"))
            assertFalse(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun happyFlowWithOnlyIntermediateCertifcate() = runTest {
        val trustManager = LocalTrustManager.create(EphemeralStorage())

        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(2, it.trustChain!!.certificates.size)
            assertEquals(intermediateCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun happyFlowWithChainOfTwo() = runTest {
        val trustManager = LocalTrustManager.create(EphemeralStorage())

        trustManager.addTrustPoint(TrustPoint(caCertificate))

        trustManager.verify(listOf(dsCertificate, intermediateCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(3, it.trustChain!!.certificates.size)
            assertEquals(caCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun trustPointNotCaCert() = runTest {
        val trustManager = LocalTrustManager.create(EphemeralStorage())

        trustManager.addTrustPoint(TrustPoint(dsCertificate))

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(1, it.trustChain!!.certificates.size)
            assertEquals(dsCertificate, it.trustChain.certificates.last())
        }
    }

    @Test
    fun happyFlowMultipleCerts() = runTest {
        val trustManager = LocalTrustManager.create(EphemeralStorage())

        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))
        trustManager.addTrustPoint(TrustPoint(caCertificate))
        trustManager.addTrustPoint(TrustPoint(ca2Certificate))

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
    fun noTrustPoints() = runTest {
        val trustManager = LocalTrustManager.create(EphemeralStorage())

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals("No trusted root certificate could not be found", it.error?.message)
            assertFalse(it.isTrusted)
            assertNull(it.trustChain)
        }
    }

    @Test
    fun skiAlreadyExists() = runTest {
        val storage = EphemeralStorage()
        val trustManager = LocalTrustManager.create(storage)
        val intermediateTrustPoint = TrustPoint(intermediateCertificate)
        val caTrustPoint = TrustPoint(caCertificate)
        trustManager.addTrustPoint(intermediateTrustPoint)
        trustManager.addTrustPoint(caTrustPoint)

        val e = assertFailsWith(TrustPointAlreadyExistsException::class) {
            trustManager.addTrustPoint(caTrustPoint)
        }
        assertEquals("TrustPoint with given SubjectKeyIdentifier already exists", e.message)
    }

    @Test
    fun persistence() = runTest {
        val storage = EphemeralStorage()
        val trustManager = LocalTrustManager.create(storage)

        val intermediateTrustPoint = TrustPoint(intermediateCertificate)
        val caTrustPoint = TrustPoint(caCertificate)
        val ca2TrustPoint = TrustPoint(ca2Certificate)
        trustManager.addTrustPoint(intermediateTrustPoint)
        trustManager.addTrustPoint(caTrustPoint)
        trustManager.addTrustPoint(ca2TrustPoint)
        assertEquals(setOf(
            caCertificate.subjectKeyIdentifier!!.toHex(),
            ca2Certificate.subjectKeyIdentifier!!.toHex(),
            intermediateCertificate.subjectKeyIdentifier!!.toHex(),
        ), trustManager.getTrustPointSkis())
        assertTrue(trustManager.deleteTrustPoint(ca2Certificate.subjectKeyIdentifier!!.toHex()))
        assertFalse(trustManager.deleteTrustPoint("nonExistent"))
        assertEquals(setOf(
            caCertificate.subjectKeyIdentifier!!.toHex(),
            intermediateCertificate.subjectKeyIdentifier!!.toHex(),
        ), trustManager.getTrustPointSkis())

        val otherTrustManager = LocalTrustManager.create(storage)
        assertEquals(setOf(
            caCertificate.subjectKeyIdentifier!!.toHex(),
            intermediateCertificate.subjectKeyIdentifier!!.toHex(),
        ), otherTrustManager.getTrustPointSkis())
        assertEquals(intermediateTrustPoint, otherTrustManager.getTrustPoint(
            intermediateCertificate.subjectKeyIdentifier!!.toHex()
        ))
        assertEquals(caTrustPoint, otherTrustManager.getTrustPoint(
            caCertificate.subjectKeyIdentifier!!.toHex()
        ))
        assertEquals(null, otherTrustManager.getTrustPoint(
            ca2Certificate.subjectKeyIdentifier!!.toHex()
        ))

        val otherStorage = EphemeralStorage()
        val yetAnotherTrustManager = LocalTrustManager.create(otherStorage)
        assertEquals(emptySet(), yetAnotherTrustManager.getTrustPointSkis())
        assertEquals(null, yetAnotherTrustManager.getTrustPoint(
            intermediateCertificate.subjectKeyIdentifier!!.toHex()
        ))
        assertEquals(null, yetAnotherTrustManager.getTrustPoint(
            caCertificate.subjectKeyIdentifier!!.toHex()
        ))
        assertEquals(null, yetAnotherTrustManager.getTrustPoint(
            ca2Certificate.subjectKeyIdentifier!!.toHex()
        ))
    }
}