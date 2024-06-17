package com.android.identity.identityctl

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.X509Certificate
import com.android.identity.crypto.X509CertificateCreateOption
import com.android.identity.crypto.X509CertificateExtension
import com.android.identity.crypto.create
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.mdoc.util.MdocUtil
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.BigIntegers
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.Security
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

@OptIn(ExperimentalEncodingApi::class)
object IdentityCtl {


    /**
     * Generates a self-signed IACA certificate according to ISO/IEC 18013-5:2021 Annex B.1.2.
     *
     * @param iacaKey the private key.
     * @param subject the value to use for subject and issuer, e.g. "CN=Test IACA,C=UT".
     * @param validFrom the point in time the certificate should be valid from.
     * @param validUntil the point in time the certificate should be valid until.
     * @param issuerAltName the issuer alternative name (see RFC 5280 section 4.2.1.7),
     * e.g. "http://issuer.example.com/informative/web/page".
     * @param crlUrl the URL for revocation (see RFC 5280 section 4.2.1.13).
     * @return a [Certificate] with all the required extensions.
     */
    @JvmStatic
    fun generateIacaCertificate(
        iacaKey: EcPrivateKey,
        subject: String,
        validFrom: Instant,
        validUntil: Instant,
        issuerAltName: String,
        crlUrl: String
    ): X509Certificate {
        // Requirements for the IACA certificate is defined in ISO/IEC 18013-5:2021 Annex B

        // From 18013-5 table B.1: countryName is mandatory
        //                         stateOrProvinceName is optional.
        //                         organizationName is optional.
        //                         commonName shall be present.
        //                         serialNumber is optional.
        //

        // From 18013-5 Annex B: 3-5 years is recommended
        //                       Maximum of 20 years after “Not before” date

        val curve = iacaKey.curve

        // From 18013-5 table B.1: Non-sequential positive, non-zero integer, shall contain
        //                         at least 63 bits of output from a CSPRNG, should contain at
        //                         least 71 bits of output from a CSPRNG, maximum 20 octets.
        val serial = BigIntegers.fromUnsignedByteArray(Random.Default.nextBytes(16)).toString()

        val extensions = mutableListOf<X509CertificateExtension>()

        // From 18013-5 table B.1: critical: Key certificate signature + CRL signature bits set
        extensions.add(
            X509CertificateExtension(
                Extension.keyUsage.toString(),
                true,
                KeyUsage(KeyUsage.cRLSign + KeyUsage.keyCertSign).encoded
            )
        )

        // From 18013-5 table B.1: non-critical, Email or URL

        extensions.add(
            X509CertificateExtension(
                Extension.issuerAlternativeName.toString(),
                false,
                GeneralName(GeneralName.uniformResourceIdentifier, issuerAltName).encoded
            )
        )

        // From 18013-5 table B.1: critical, CA=true, pathLenConstraint=0
        extensions.add(
            X509CertificateExtension(
                Extension.basicConstraints.toString(),
                true,
                BasicConstraints(0).encoded
            )
        )

        // From 18013-5 table B.1: non-critical, The ‘reasons’ and ‘cRL Issuer’
        // fields shall not be used.
        val distributionPoint =
            DistributionPoint(
                DistributionPointName(
                    GeneralNames(
                        GeneralName(GeneralName.uniformResourceIdentifier, crlUrl)
                    )
                ),
                null,
                null
            )
        extensions.add(
            X509CertificateExtension(
                Extension.cRLDistributionPoints.toString(),
                false,
                CRLDistPoint(listOf(distributionPoint).toTypedArray()).encoded
            )
        )

        return X509Certificate.create(
            iacaKey.publicKey,
            iacaKey,
            null,
            curve.defaultSigningAlgorithm,
            serial,
            subject,
            subject,
            validFrom,
            validUntil,
            // 18013-5 Annex C requires both of these to be present
            setOf(
                X509CertificateCreateOption.INCLUDE_SUBJECT_KEY_IDENTIFIER,
                X509CertificateCreateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_AS_SUBJECT_KEY_IDENTIFIER
            ),
            extensions
        )
    }

    /**
     * Generates a Document Signing certificate according to ISO/IEC 18013-5:2021 Annex B.1.4.
     *
     * @param iacaCert the IACA certificate the DS certificate.
     * @param iacaKey the private key for the IACA certificate.
     * @param dsKey the public part of the DS key.
     * @param subject the value to use for subject, e.g. "CN=Test DS,C=UT".
     * @param validFrom the point in time the certificate should be valid from.
     * @param validUntil the point in time the certificate should be valid until.
     * @param issuerAltName the issuer alternative name (see RFC 5280 section 4.2.1.7),
     * e.g. "http://issuer.example.com/informative/web/page".
     * @param crlUrl the URL for revocation (see RFC 5280 section 4.2.1.13).
     * @return a [Certificate] with all the required extensions.
     */
    @JvmStatic
    fun generateDsCertificate(
        iacaCert: X509Certificate,
        iacaKey: EcPrivateKey,
        dsKey: EcPublicKey,
        subject: String,
        validFrom: Instant,
        validUntil: Instant,
    ): X509Certificate {

        val iacaCertJava = iacaCert.javaX509Certificate

        // Must be same exact binary value as the subject of IACA certificate.
        val issuer = iacaCertJava.subjectX500Principal.toString()

        val serial = BigIntegers.fromUnsignedByteArray(Random.Default.nextBytes(16)).toString()

        val extensions = mutableListOf<X509CertificateExtension>()

        extensions.add(
            X509CertificateExtension(
                Extension.keyUsage.toString(),
                true,
                KeyUsage(KeyUsage.digitalSignature).encoded
            )
        )

        extensions.add(
            X509CertificateExtension(
                Extension.extendedKeyUsage.toString(),
                true,
                ExtendedKeyUsage(
                    KeyPurposeId.getInstance(ASN1ObjectIdentifier("1.0.18013.5.1.2"))
                ).encoded
            )
        )

        // Copy cRLDistributionPoints and issuerAlternativeName from IACA cert
        extensions.add(
            X509CertificateExtension(
                Extension.cRLDistributionPoints.toString(),
                false,
                iacaCertJava.getExtensionValue(Extension.cRLDistributionPoints.toString())
            )
        )
        extensions.add(
            X509CertificateExtension(
                Extension.issuerAlternativeName.toString(),
                false,
                iacaCertJava.getExtensionValue(Extension.issuerAlternativeName.toString())
            )
        )

        val documentSigningKeyCert = X509Certificate.create(
            dsKey,
            iacaKey,
            iacaCert,
            Algorithm.ES256,
            serial,
            subject,
            issuer,
            validFrom,
            validUntil,
            setOf(
                X509CertificateCreateOption.INCLUDE_SUBJECT_KEY_IDENTIFIER,
                X509CertificateCreateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_FROM_SIGNING_KEY_CERTIFICATE
            ),
            extensions
        )

        return documentSigningKeyCert
    }


    fun getArg(
        args: Array<String>,
        argName: String,
        defaultValue: String
        ): String {
        val prefixedArgName = "--" + argName
        for (n in IntRange(0, args.size - 1)) {
            val arg = args[n]
            if (arg.equals(prefixedArgName)) {
                if (n + 1 < args.size) {
                    return args[n + 1]
                }
            }
        }
        return defaultValue
    }

    fun generateIaca(args: Array<String>) {
        val certificateOutputFilename =
            getArg(args,"out_certificate","iaca_certificate.pem")
        val privateKeyOutputFilename =
            getArg(args,"out_private_key","iaca_private_key.pem")

        // Requirements for the IACA certificate is defined in ISO/IEC 18013-5:2021 Annex B

        // From 18013-5 table B.1: countryName is mandatory
        //                         stateOrProvinceName is optional.
        //                         organizationName is optional.
        //                         commonName shall be present.
        //                         serialNumber is optional.
        //
        val subjectAndIssuer =
            getArg(args,"subject_and_issuer",
                "CN=OWF Identity Credential TEST IACA,C=UT")

        // From 18013-5 Annex B: 3-5 years is recommended
        //                       Maximum of 20 years after “Not before” date
        val validityInYears = getArg(args,"validity_in_years","5").toInt()
        val now = Clock.System.now()
        val validFrom = now
        val validUntil = now.plus(DateTimePeriod(years = validityInYears), TimeZone.currentSystemDefault())

        val curveName = getArg(args,"curve","P384")
        val curve = EcCurve.values().find { curve -> curve.name == curveName }
            ?: throw IllegalArgumentException("No curve with name $curveName")

        val iacaKey = Crypto.createEcPrivateKey(curve)

        val issuerAltName = getArg(args,
            "issuer_alt_name",
            "https://github.com/openwallet-foundation-labs/identity-credential"
        )

        val crlUrl = getArg(
            args,
            "crl_url",
            "https://github.com/openwallet-foundation-labs/identity-credential"
        )

        val iacaCertificate = generateIacaCertificate(
            iacaKey,
            subjectAndIssuer,
            validFrom,
            validUntil,
            issuerAltName,
            crlUrl
        )

        println("Generated self-signed IACA certificate and private key.")

        File(privateKeyOutputFilename).outputStream().bufferedWriter().let {
            it.write(iacaKey.toPem())
            it.close()
        }
        println("- Wrote private key to $privateKeyOutputFilename")

        File(certificateOutputFilename).outputStream().bufferedWriter().let {
            it.write(iacaCertificate.toPem())
            it.close()
        }
        println("- Wrote IACA certificate to $certificateOutputFilename")
    }

    fun generateDs(args: Array<String>) {
        val iacaCertificateFilename =
            getArg(args,"iaca_certificate","iaca_certificate.pem")
        val iacaPrivateKeyFilename =
            getArg(args,"iaca_private_key","iaca_private_key.pem")

        val iacaCert = X509Certificate.fromPem(
            String(File(iacaCertificateFilename).readBytes(), StandardCharsets.US_ASCII))

        val iacaPrivateKey = EcPrivateKey.fromPem(
            String(File(iacaPrivateKeyFilename).readBytes(), StandardCharsets.US_ASCII),
            iacaCert.ecPublicKey)

        val certificateOutputFilename =
            getArg(args,"out_certificate","ds_certificate.pem")
        val privateKeyOutputFilename =
            getArg(args,"out_private_key","ds_private_key.pem")

        // Requirements for the IACA certificate is defined in ISO/IEC 18013-5:2021 Annex B

        val subject = getArg(args,"subject", "CN=OWF Identity Credential TEST DS,C=UT")

        val validityInYears = getArg(args,"validity_in_years","1").toInt()
        val now = Clock.System.now()
        val validFrom = now
        val validUntil = now.plus(DateTimePeriod(years = validityInYears), TimeZone.currentSystemDefault())

        val curveName = getArg(args,"curve","P256")
        val curve = EcCurve.values().find { curve -> curve.name == curveName }
            ?: throw IllegalArgumentException("No curve with name $curveName")

        val dsKey = Crypto.createEcPrivateKey(curve)

        val dsCertificate = generateDsCertificate(
            iacaCert,
            iacaPrivateKey,
            dsKey.publicKey,
            subject,
            validFrom,
            validUntil
        )

        println("Generated DS certificate and private key.")

        File(privateKeyOutputFilename).outputStream().bufferedWriter().let {
            it.write(dsKey.toPem())
            it.close()
        }
        println("- Wrote private key to $privateKeyOutputFilename")

        File(certificateOutputFilename).outputStream().bufferedWriter().let {
            it.write(dsCertificate.toPem())
            it.close()
        }
        println("- Wrote DS certificate to $certificateOutputFilename")
    }

    fun generateReaderRoot(args: Array<String>) {
        val certificateOutputFilename =
            getArg(args,"out_certificate","reader_root_certificate.pem")
        val privateKeyOutputFilename =
            getArg(args,"out_private_key","reader_root_private_key.pem")

        val subjectAndIssuer =
            getArg(args,"subject_and_issuer",
                "CN=OWF Identity Credential TEST Reader CA,C=UT")

        // From 18013-5 Annex B: 3-5 years is recommended
        //                       Maximum of 20 years after “Not before” date
        val validityInYears = getArg(args,"validity_in_years","5").toInt()
        val now = Clock.System.now()
        val validFrom = now
        val validUntil = now.plus(DateTimePeriod(years = validityInYears), TimeZone.currentSystemDefault())

        val curveName = getArg(args,"curve","P384")
        val curve = EcCurve.values().find { curve -> curve.name == curveName }
            ?: throw IllegalArgumentException("No curve with name $curveName")

        val serial = BigIntegers.fromUnsignedByteArray(Random.Default.nextBytes(16)).toString()

        val readerRootKey = Crypto.createEcPrivateKey(curve)

        val extensions = mutableListOf<X509CertificateExtension>()

        extensions.add(
            X509CertificateExtension(
                Extension.keyUsage.toString(),
                true,
                KeyUsage(KeyUsage.cRLSign + KeyUsage.keyCertSign).encoded
            )
        )

        val readerRootCertificate = X509Certificate.create(
            readerRootKey.publicKey,
            readerRootKey,
            null,
            curve.defaultSigningAlgorithm,
            serial,
            subjectAndIssuer,
            subjectAndIssuer,
            validFrom,
            validUntil,
            setOf(
                X509CertificateCreateOption.INCLUDE_SUBJECT_KEY_IDENTIFIER,
                X509CertificateCreateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_AS_SUBJECT_KEY_IDENTIFIER
            ),
            extensions
        )

        println("Generated self-signed reader root certificate and private key.")

        File(privateKeyOutputFilename).outputStream().bufferedWriter().let {
            it.write(readerRootKey.toPem())
            it.close()
        }
        println("- Wrote private key to $privateKeyOutputFilename")

        File(certificateOutputFilename).outputStream().bufferedWriter().let {
            it.write(readerRootCertificate.toPem())
            it.close()
        }
        println("- Wrote reader root certificate to $certificateOutputFilename")
    }


    fun usage(args: Array<String>) {
        println(
"""
Generate an IACA certificate and corresponding private key:

    identityctl generateIaca
        [--out_certificate iaca_certificate.pem]
        [--out_private_key iaca_private_key.pem]
        [--subject_and_issuer 'CN=Utopia TEST IACA,C=UT']
        [--validity_in_years 5]
        [--curve P384]
        [--issuer_alt_name https://issuer.example.com/website]
        [--crl_url https://issuer.example.com/crl.crl]

    identityctl generateDs
        --iaca_certificate iaca_certificate.pem
        --iaca_private_key iaca_private_key.pem
        [--out_certificate ds_certificate.pem]
        [--out_private_key ds_private_key.pem]
        [--subject_and_issuer 'CN=Utopia TEST DS,C=UT']
        [--validity_in_years 1]
        [--curve P256]

    identityctl generateReaderRoot
        [--out_certificate reader_root_certificate.pem]
        [--out_private_key reader_root_private_key.pem]
        [--subject_and_issuer 'CN=Utopia TEST Reader CA,C=UT']
        [--validity_in_years 3]
        [--curve P384]
""")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        if (args.size == 0) {
            usage(args)
        } else {
            val command = args[0]
            when (command) {
                "generateIaca" -> generateIaca(args)
                "generateDs" -> generateDs(args)
                "generateReaderRoot" -> generateReaderRoot(args)
                "help" -> usage(args)
                else -> {
                    println("Unknown command '$command'")
                    usage(args)
                }
            }
        }
    }

}
