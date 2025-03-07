package com.android.mdl.appreader.readercertgen

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.javaPrivateKey
import org.multipaz.crypto.javaPublicKey
import org.multipaz.crypto.javaX509Certificate
import com.android.mdl.appreader.readercertgen.CertificateGenerator.generateCertificate
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
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
        readerKey: EcPrivateKey, //dsKeyPair: KeyPair,
        readerRootCert: X509Cert, // issuerCert: X509Certificate,
        readerRootKey: EcPrivateKey // issuerPrivateKey: PrivateKey
    ): java.security.cert.X509Certificate {
        val data = DataMaterial(
            subjectDN = "C=ZZ, CN=OWF Identity Credential mDoc Reader",

            // must match DN of issuer character-by-character
            // TODO change for other generators
            issuerDN = readerRootCert.javaX509Certificate.subjectX500Principal.name,
            // reorders string, do not use
            // return issuerCert.getSubjectX500Principal().getName();

            // NOTE always interpreted as URL for now
            issuerAlternativeName = Optional.of("https://www.google.com/")
        )
        val certData = CertificateMaterial(
            // TODO change
            serialNumber = BigInteger("476f6f676c655f546573745f44535f31", 16),
            startDate = readerRootCert.javaX509Certificate.notBefore,
            endDate = readerRootCert.javaX509Certificate.notAfter,
            pathLengthConstraint = CertificateMaterial.PATHLENGTH_NOT_A_CA,
            keyUsage = KeyUsage.digitalSignature,
            // TODO change for reader cert
            extendedKeyUsage = Optional.of("1.0.18013.5.1.6")
        )

        val signingAlgorithm = when (readerRootKey.curve.defaultSigningAlgorithm) {
            Algorithm.ES256 -> "SHA256withECDSA"
            Algorithm.ES384 -> "SHA384withECDSA"
            Algorithm.ES512 -> "SHA512withECDSA"
            else -> throw IllegalStateException("Unsupported algorithm for reader root")
        }

        val keyData = KeyMaterial(
            publicKey = readerKey.publicKey.javaPublicKey,
            signingAlgorithm = signingAlgorithm,
            signingKey = readerRootKey.javaPrivateKey,
            issuerCertificate = Optional.of(readerRootCert.javaX509Certificate)
        )

        // C.1.7.2
        return generateCertificate(data, certData, keyData)
    }
}