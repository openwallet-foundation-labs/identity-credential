package org.multipaz.multipazctl

import org.multipaz.asn1.ASN1Integer
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.mdoc.util.MdocUtil
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.BigIntegers
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.Security
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

@OptIn(ExperimentalEncodingApi::class)
object MultipazCtl {

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
            getArg(args,"out_certificate", "iaca_certificate.pem")
        val privateKeyOutputFilename =
            getArg(args,"out_private_key", "iaca_private_key.pem")

        // Requirements for the IACA certificate is defined in ISO/IEC 18013-5:2021 Annex B

        // From 18013-5 table B.1: countryName is mandatory
        //                         stateOrProvinceName is optional.
        //                         organizationName is optional.
        //                         commonName shall be present.
        //                         serialNumber is optional.
        //
        val subjectAndIssuer = X500Name.fromName(
            getArg(args, "subject_and_issuer", "CN=OWF Identity Credential TEST IACA,C=ZZ")
        )

        // From 18013-5 Annex B: 3-5 years is recommended
        //                       Maximum of 20 years after “Not before” date
        val validityInYears = getArg(args, "validity_in_years", "5").toInt()
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val validFrom = now
        val validUntil = now.plus(DateTimePeriod(years = validityInYears), TimeZone.currentSystemDefault())

        val curveName = getArg(args, "curve", "P384")
        val curve = EcCurve.values().find { curve -> curve.name == curveName }
            ?: throw IllegalArgumentException("No curve with name $curveName")

        val iacaKey = Crypto.createEcPrivateKey(curve)

        val issuerAltNameUrl = getArg(args,
            "issuer_alt_name_url",
            "https://github.com/openwallet-foundation-labs/identity-credential"
        )

        val crlUrl = getArg(
            args,
            "crl_url",
            "https://github.com/openwallet-foundation-labs/identity-credential"
        )

        val serial = ASN1Integer(BigIntegers.fromUnsignedByteArray(Random.Default.nextBytes(16)).toByteArray())

        val iacaCertificate = MdocUtil.generateIacaCertificate(
            iacaKey,
            subjectAndIssuer,
            serial,
            validFrom,
            validUntil,
            issuerAltNameUrl,
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
            getArg(args, "iaca_certificate", "iaca_certificate.pem")
        val iacaPrivateKeyFilename =
            getArg(args, "iaca_private_key", "iaca_private_key.pem")

        val iacaCert = X509Cert.fromPem(
            String(File(iacaCertificateFilename).readBytes(), StandardCharsets.US_ASCII))

        val iacaPrivateKey = EcPrivateKey.fromPem(
            String(File(iacaPrivateKeyFilename).readBytes(), StandardCharsets.US_ASCII),
            iacaCert.ecPublicKey)

        val certificateOutputFilename =
            getArg(args, "out_certificate", "ds_certificate.pem")
        val privateKeyOutputFilename =
            getArg(args, "out_private_key", "ds_private_key.pem")

        // Requirements for the IACA certificate is defined in ISO/IEC 18013-5:2021 Annex B

        val subject = X500Name.fromName(
            getArg(args, "subject", "CN=OWF Identity Credential TEST DS,C=ZZ")
        )

        val validityInYears = getArg(args, "validity_in_years", "1").toInt()
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val validFrom = now
        val validUntil = now.plus(DateTimePeriod(years = validityInYears), TimeZone.currentSystemDefault())

        val curveName = getArg(args, "curve", "P256")
        val curve = EcCurve.values().find { curve -> curve.name == curveName }
            ?: throw IllegalArgumentException("No curve with name $curveName")

        val dsKey = Crypto.createEcPrivateKey(curve)

        val serial = ASN1Integer(BigIntegers.fromUnsignedByteArray(Random.Default.nextBytes(16)).toByteArray())

        val dsCertificate = MdocUtil.generateDsCertificate(
            iacaCert,
            iacaPrivateKey,
            dsKey.publicKey,
            subject,
            serial,
            validFrom,
            validUntil
        )

        println("Generated DS certificate and private key.")

        println("- Loaded IACA cert from $iacaCertificateFilename")

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
            getArg(args, "out_certificate", "reader_root_certificate.pem")
        val privateKeyOutputFilename =
            getArg(args, "out_private_key", "reader_root_private_key.pem")

        val subjectAndIssuer = X500Name.fromName(
            getArg(args, "subject_and_issuer", "CN=OWF Identity Credential TEST Reader CA,C=ZZ")
        )

        // From 18013-5 Annex B: 3-5 years is recommended
        //                       Maximum of 20 years after “Not before” date
        val validityInYears = getArg(args, "validity_in_years", "5").toInt()
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val validFrom = now
        val validUntil = now.plus(DateTimePeriod(years = validityInYears), TimeZone.currentSystemDefault())

        val curveName = getArg(args, "curve", "P384")
        val curve = EcCurve.values().find { curve -> curve.name == curveName }
            ?: throw IllegalArgumentException("No curve with name $curveName")

        val serial = ASN1Integer(BigIntegers.fromUnsignedByteArray(Random.Default.nextBytes(16)).toByteArray())

        val readerRootKey = Crypto.createEcPrivateKey(curve)

        val readerRootCertificate = X509Cert.Builder(
            publicKey = readerRootKey.publicKey,
            signingKey = readerRootKey,
            signatureAlgorithm = readerRootKey.curve.defaultSigningAlgorithm,
            serialNumber = serial,
            subject = subjectAndIssuer,
            issuer = subjectAndIssuer,
            validFrom = validFrom,
            validUntil = validUntil
        )
            .includeSubjectKeyIdentifier()
            .setKeyUsage(setOf(X509KeyUsage.CRL_SIGN, X509KeyUsage.KEY_CERT_SIGN))
            .setBasicConstraints(true, 0)
            .build()

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

    multipazctl generateIaca
        [--out_certificate iaca_certificate.pem]
        [--out_private_key iaca_private_key.pem]
        [--subject_and_issuer 'CN=OWF Identity Credential TEST IACA,C=ZZ']
        [--validity_in_years 5]
        [--curve P384]
        [--issuer_alt_name_url https://issuer.example.com/website]
        [--crl_url https://issuer.example.com/crl.crl]

Generate an DS certificate and corresponding private key:

    multipazctl generateDs
        --iaca_certificate iaca_certificate.pem
        --iaca_private_key iaca_private_key.pem
        [--out_certificate ds_certificate.pem]
        [--out_private_key ds_private_key.pem]
        [--subject 'CN=OWF Identity Credential TEST DS,C=ZZ']
        [--validity_in_years 1]
        [--curve P256]

Generate an reader root and corresponding private key:

    multipazctl generateReaderRoot
        [--out_certificate reader_root_certificate.pem]
        [--out_private_key reader_root_private_key.pem]
        [--subject_and_issuer 'CN=OWF Identity Credential TEST Reader CA,C=ZZ']
        [--validity_in_years 3]
        [--curve P384]

    multipazctl version
""")
    }

    fun version(args: Array<String>) {
        println(BuildConfig.VERSION)
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
                "version" -> version(args)
                else -> {
                    println("Unknown command '$command'")
                    usage(args)
                }
            }
        }
    }
}
