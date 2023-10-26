package com.android.mdl.appreader.readercertgen

import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.Optional

/**
 * Generates a key pair for a specific curve, creates a reader certificate around it using
 * the given issuing CA certificate and private key to sign it.
 *
 *
 * Usage:
 * val readerKeyPair = generateECDSAKeyPair(curve)
 * val dsCertificate = createReaderCertificate(readerKeyPair, iacaCertificate, iacaKeyPair.getPrivate())
 */
object ReaderCertificateGenerator {
    fun generateECDSAKeyPair(curve: String): KeyPair {
        return try {
            // NOTE older devices may not have the right BC installed for this to work
            val kpg: KeyPairGenerator
            if (curve.equals("Ed25519", ignoreCase = true) || curve.equals(
                    "Ed448",
                    ignoreCase = true
                )
            ) {
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
        val data: DataMaterial = object : DataMaterial {
            override fun subjectDN(): String {
                return "C=UT, CN=Google mDoc Reader"
            }

            override fun issuerDN(): String {
                // must match DN of issuer character-by-character
                // TODO change for other generators
                return issuerCert.subjectX500Principal.name

                // reorders string, do not use
                // return issuerCert.getSubjectX500Principal().getName();
            }

            override fun issuerAlternativeName(): Optional<String> {
                // NOTE always interpreted as URL for now
                return Optional.of("https://www.google.com/")
            }
        }
        val certData: CertificateMaterial = object : CertificateMaterial {
            override fun serialNumber(): BigInteger {
                // TODO change
                return BigInteger("476f6f676c655f546573745f44535f31", 16)
            }

            override fun startDate(): Date {
                return EncodingUtil.parseShortISODate("2023-01-01")
            }

            override fun endDate(): Date {
                return EncodingUtil.parseShortISODate("2024-01-01")
            }

            override fun pathLengthConstraint(): Int {
                return CertificateMaterial.PATHLENGTH_NOT_A_CA
            }

            override fun keyUsage(): Int {
                return KeyUsage.digitalSignature
            }

            override fun extendedKeyUsage(): Optional<String> {
                // TODO change for reader cert
                return Optional.of("1.0.18013.5.1.6")
            }
        }
        val keyData: KeyMaterial = object : KeyMaterial {
            override fun publicKey(): PublicKey {
                return dsKeyPair.public
            }

            override fun signingAlgorithm(): String {
                return "SHA384WithECDSA"
            }

            override fun signingKey(): PrivateKey {
                return issuerPrivateKey
            }

            override fun issuerCertificate(): Optional<X509Certificate> {
                return Optional.of(issuerCert)
            }
        }

        // C.1.7.2
        return CertificateGenerator.generateCertificate(data, certData, keyData)
    }
}