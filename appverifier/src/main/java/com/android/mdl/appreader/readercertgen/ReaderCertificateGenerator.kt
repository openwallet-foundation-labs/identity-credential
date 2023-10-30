package com.android.mdl.appreader.readercertgen

import com.android.mdl.appreader.readercertgen.CertificateGenerator.generateCertificate
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Optional

/**
 * Generates a key pair for a specific curve, creates a reader certificate around it using
 * the given issuing CA certificate and private key to sign it.
 *
 *
 * Usage:
 * final KeyPair readerKeyPair = generateECDSAKeyPair(curve);
 * X509Certificate dsCertificate = createReaderCertificate(readerKeyPair, iacaCertificate, iacaKeyPair.getPrivate());
 */
object ReaderCertificateGenerator {
    fun generateECDSAKeyPair(curve: String): KeyPair {
        return try {
            // NOTE older devices may not have the right BC installed for this to work
            val kpg: KeyPairGenerator
            if (listOf("Ed25519", "Ed448").any { it.equals(curve, ignoreCase = true) }) {
                kpg = KeyPairGenerator.getInstance(curve, BouncyCastleProvider())
            } else {
                kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
                kpg.initialize(ECGenParameterSpec(curve))
            }
            println(kpg.provider.info)
            kpg.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw RuntimeException(e)
        }
    }

    fun createReaderCertificate(
        dsKeyPair: KeyPair, issuerCert: X509Certificate,
        issuerPrivateKey: PrivateKey
    ): X509Certificate {
        val data = DataMaterial(
            subjectDN = "C=UT, CN=Google mDoc Reader",

            // must match DN of issuer character-by-character
            // TODO change for other generators
            issuerDN = issuerCert.subjectX500Principal.name,
            // reorders string, do not use
            // return issuerCert.getSubjectX500Principal().getName();

            // NOTE always interpreted as URL for now
            issuerAlternativeName = Optional.of("https://www.google.com/")
        )
        val certData = CertificateMaterial(
            // TODO change
            serialNumber = BigInteger("476f6f676c655f546573745f44535f31", 16),
            startDate = EncodingUtil.parseShortISODate("2023-01-01"),
            endDate = EncodingUtil.parseShortISODate("2024-01-01"),
            pathLengthConstraint = CertificateMaterial.PATHLENGTH_NOT_A_CA,
            keyUsage = KeyUsage.digitalSignature,
            // TODO change for reader cert
            extendedKeyUsage = Optional.of("1.0.18013.5.1.6")
        )

        val keyData = KeyMaterial(
            publicKey = dsKeyPair.public,
            signingAlgorithm = "SHA384WithECDSA",
            signingKey = issuerPrivateKey,
            issuerCertificate = Optional.of(issuerCert)
        )

        // C.1.7.2
        return generateCertificate(data, certData, keyData)
    }
}