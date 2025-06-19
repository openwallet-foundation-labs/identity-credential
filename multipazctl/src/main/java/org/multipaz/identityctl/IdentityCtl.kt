package org.multipaz.multipazctl

import org.multipaz.asn1.ASN1Integer
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.mdoc.util.MdocUtil
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.BigIntegers
import org.multipaz.crypto.X509CertChain
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.Security
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

@OptIn(ExperimentalEncodingApi::class)
object MultipazCtl {

    private val jsonPrettyPrint = Json { prettyPrint = true }

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

        val curveName = getArg(args, "curve", "P-384")
        val curve = EcCurve.fromJwkName(curveName)

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

        val curveName = getArg(args, "curve", "P-256")
        val curve = EcCurve.fromJwkName(curveName)

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
        val subjectAndIssuer = X500Name.fromName(
            getArg(args, "subject_and_issuer", "CN=OWF Identity Credential TEST Reader CA,C=ZZ")
        )

        // From 18013-5 Annex B: 3-5 years is recommended
        //                       Maximum of 20 years after “Not before” date
        val validityInYears = getArg(args, "validity_in_years", "5").toInt()
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val validFrom = now
        val validUntil = now.plus(DateTimePeriod(years = validityInYears), TimeZone.currentSystemDefault())

        val curveName = getArg(args, "curve", "P-384")
        val curve = EcCurve.fromJwkName(curveName)

        val serial = ASN1Integer(BigIntegers.fromUnsignedByteArray(Random.Default.nextBytes(16)).toByteArray())


        println("Curve: $curve [$curveName]")

        val readerRootKey = Crypto.createEcPrivateKey(curve)

        val readerRootCertificate =
            MdocUtil.generateReaderRootCertificate(
                readerRootKey = readerRootKey,
                subject = subjectAndIssuer,
                serial = serial,
                validFrom = validFrom,
                validUntil = validUntil,
                crlUrl = "https://github.com/openwallet-foundation-labs/identity-credential/crl"
            )

        println("Generated self-signed reader root certificate and private key.")

        val certificateOutputFilename = getArg(args, "out_certificate", "")
        val privateKeyOutputFilename = getArg(args, "out_private_key", "")
        if (certificateOutputFilename.isNotEmpty()) {
            check(privateKeyOutputFilename.isNotEmpty()) {
                "When out_certificate is specified, out_private_key must be specified too"
            }
            File(privateKeyOutputFilename).writer().let {
                it.write(readerRootKey.toPem())
                it.close()
            }
            println("- Wrote private key to $privateKeyOutputFilename")

            File(certificateOutputFilename).writer().let {
                it.write(readerRootCertificate.toPem())
                it.close()
            }
            println("- Wrote reader root certificate to $certificateOutputFilename")
        } else {
            val readerIdentity = getArg(args, "out_identity", "reader_identity.json")
            val json = buildJsonObject {
                put("jwk", readerRootKey.toJwk())
                put("x5c", X509CertChain(listOf(readerRootCertificate)).toX5c())
            }
            File(readerIdentity).writer().let {
                it.write(jsonPrettyPrint.encodeToString(json))
                it.write("\n")
                it.close()
            }
        }
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
        [--curve P-384]
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
        [--curve P-256]

Generate an reader root and corresponding private key:

    multipazctl generateReaderRoot
        [--out_identity reader_identity.json]
        [--out_certificate <no default>]
        [--out_private_key <no default>]
        [--subject_and_issuer 'CN=OWF Identity Credential TEST Reader CA,C=ZZ']
        [--validity_in_years 3]
        [--curve P-384]

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
