package com.android.identity.mdoc.vical

import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.X509CertificateCreateOption
import com.android.identity.crypto.create
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class VicalGeneratorTest {

    private fun createSelfsignedCert(
        key: EcPrivateKey,
        subject: String
    ): X509Cert {
        val now = Clock.System.now()
        val validFrom = now - 10.minutes
        val validUntil = now + 10.minutes
        return X509Cert.create(
            publicKey = key.publicKey,
            signingKey = key,
            signingKeyCertificate = null,
            signatureAlgorithm = key.curve.defaultSigningAlgorithm,
            serial = "1",
            subject = subject,
            issuer = subject,
            validFrom = validFrom,
            validUntil = validUntil,
            options = setOf(
                X509CertificateCreateOption.INCLUDE_SUBJECT_KEY_IDENTIFIER,
                X509CertificateCreateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_AS_SUBJECT_KEY_IDENTIFIER
            ),
            additionalExtensions = listOf()
        )
    }

    @Test
    fun testVicalGenerator() {
        val vicalKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val vicalCert = createSelfsignedCert(vicalKey, "CN=Test VICAL")

        val issuer1Cert = createSelfsignedCert(Crypto.createEcPrivateKey(EcCurve.P256), "CN=Issuer 1 IACA")
        val issuer2Cert = createSelfsignedCert(Crypto.createEcPrivateKey(EcCurve.P256), "CN=Issuer 2 IACA")
        val issuer3Cert = createSelfsignedCert(Crypto.createEcPrivateKey(EcCurve.P256), "CN=Issuer 3 IACA")

        val vicalDate = Clock.System.now()
        val vicalNextUpdate = vicalDate + 30.days
        val vicalIssueID = 42L

        val signedVical = SignedVical(
            vical = Vical(
                version = "1.0",
                vicalProvider = "Test VICAL Provider",
                date = vicalDate,
                nextUpdate = vicalNextUpdate,
                vicalIssueID = vicalIssueID,
                listOf(
                    VicalCertificateInfo(
                        certificate = issuer1Cert.encodedCertificate,
                        docType = listOf("org.iso.18013.5.1.mDL"),
                        certificateProfiles = listOf("")
                    ),
                    VicalCertificateInfo(
                        certificate = issuer2Cert.encodedCertificate,
                        docType = listOf("org.iso.18013.5.1.mDL"),
                        certificateProfiles = null
                    ),
                    VicalCertificateInfo(
                        certificate = issuer3Cert.encodedCertificate,
                        docType = listOf("org.iso.18013.5.1.mDL", "eu.europa.ec.eudi.pid.1"),
                        certificateProfiles = null
                    ),
                )
            ),
            vicalProviderCertificateChain = X509CertChain(listOf(vicalCert))
        )
        val encodedSignedVical = signedVical.generate(
            signingKey = vicalKey,
            signingAlgorithm = vicalKey.curve.defaultSigningAlgorithm
        )

        val decodedSignedVical = SignedVical.parse(
            encodedSignedVical = encodedSignedVical
        )

        assertEquals(listOf(vicalCert), decodedSignedVical.vicalProviderCertificateChain.certificates)
        assertEquals("Test VICAL Provider", decodedSignedVical.vical.vicalProvider)
        assertEquals("1.0", decodedSignedVical.vical.version)
        assertEquals(vicalDate, decodedSignedVical.vical.date)
        assertEquals(vicalNextUpdate, decodedSignedVical.vical.nextUpdate)
        assertEquals(vicalIssueID, decodedSignedVical.vical.vicalIssueID)
        assertEquals(3, decodedSignedVical.vical.certificateInfos.size)

        assertContentEquals(issuer1Cert.encodedCertificate,
            decodedSignedVical.vical.certificateInfos[0].certificate)
        assertContentEquals(listOf("org.iso.18013.5.1.mDL"),
            decodedSignedVical.vical.certificateInfos[0].docType)
        assertEquals(null, decodedSignedVical.vical.certificateInfos[0].certificateProfiles)

        assertContentEquals(issuer2Cert.encodedCertificate,
            decodedSignedVical.vical.certificateInfos[1].certificate)
        assertContentEquals(listOf("org.iso.18013.5.1.mDL"),
            decodedSignedVical.vical.certificateInfos[1].docType)
        assertEquals(null, decodedSignedVical.vical.certificateInfos[1].certificateProfiles)

        assertContentEquals(issuer3Cert.encodedCertificate,
            decodedSignedVical.vical.certificateInfos[2].certificate)
        assertContentEquals(listOf("org.iso.18013.5.1.mDL", "eu.europa.ec.eudi.pid.1"),
            decodedSignedVical.vical.certificateInfos[2].docType)
        assertEquals(null, decodedSignedVical.vical.certificateInfos[2].certificateProfiles)
    }
}