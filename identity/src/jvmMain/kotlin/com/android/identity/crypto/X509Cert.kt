package com.android.identity.crypto

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.DataItem
import kotlinx.datetime.Instant
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual class X509Cert actual constructor(actual val encodedCertificate: ByteArray) {

    actual val toDataItem: DataItem
        get() = Bstr(encodedCertificate)

    @OptIn(ExperimentalEncodingApi::class)
    actual fun toPem(): String {
        val sb = StringBuilder()
        sb.append("-----BEGIN CERTIFICATE-----\n")
        sb.append(Base64.Mime.encode(encodedCertificate))
        sb.append("\n-----END CERTIFICATE-----\n")
        return sb.toString()    }

    fun verify(signingCertificate: X509Cert): Boolean =
        try {
            this.javaX509Certificate.verify(signingCertificate.javaX509Certificate.publicKey)
            true
        } catch (e: Throwable) {
            false
        }

    private fun getCurve(tbsCertificate: ByteArray): EcCurve {
        // We need to look in SubjectPublicKeyInfo for the curve...
        //
        //     TBSCertificate  ::=  SEQUENCE  {
        //        version         [0]  EXPLICIT Version DEFAULT v1,
        //        serialNumber         CertificateSerialNumber,
        //        signature            AlgorithmIdentifier,
        //        issuer               Name,
        //        validity             Validity,
        //        subject              Name,
        //        subjectPublicKeyInfo SubjectPublicKeyInfo,
        //        issuerUniqueID  [1]  IMPLICIT UniqueIdentifier OPTIONAL,
        //                             -- If present, version MUST be v2 or v3
        //        subjectUniqueID [2]  IMPLICIT UniqueIdentifier OPTIONAL,
        //                             -- If present, version MUST be v2 or v3
        //        extensions      [3]  EXPLICIT Extensions OPTIONAL
        //                             -- If present, version MUST be v3
        //        }
        //
        // where
        //
        //   SubjectPublicKeyInfo  ::=  SEQUENCE  {
        //        algorithm            AlgorithmIdentifier,
        //        subjectPublicKey     BIT STRING  }
        //
        //   AlgorithmIdentifier  ::=  SEQUENCE  {
        //        algorithm               OBJECT IDENTIFIER,
        //        parameters              ANY DEFINED BY algorithm OPTIONAL  }
        //
        // This is all from https://datatracker.ietf.org/doc/html/rfc5280
        //

        val input = ASN1InputStream(tbsCertificate)
        val seq = ASN1Sequence.getInstance(input.readObject())
        val subjectPublicKeyInfo = seq.getObjectAt(6) as ASN1Sequence
        val algorithmIdentifier = subjectPublicKeyInfo.getObjectAt(0) as ASN1Sequence
        val algorithmOidString = (algorithmIdentifier.getObjectAt(0) as ASN1ObjectIdentifier).id
        val curve =
            when (algorithmOidString) {
                // https://datatracker.ietf.org/doc/html/rfc5480#section-2.1.1
                "1.2.840.10045.2.1" -> {
                    val ecCurveString = (algorithmIdentifier.getObjectAt(1) as
                            ASN1ObjectIdentifier).id
                    when (ecCurveString) {
                        "1.2.840.10045.3.1.7" -> EcCurve.P256
                        "1.3.132.0.34" -> EcCurve.P384
                        "1.3.132.0.35" -> EcCurve.P521
                        "1.3.36.3.3.2.8.1.1.7" -> EcCurve.BRAINPOOLP256R1
                        "1.3.36.3.3.2.8.1.1.9" -> EcCurve.BRAINPOOLP320R1
                        "1.3.36.3.3.2.8.1.1.11" -> EcCurve.BRAINPOOLP384R1
                        "1.3.36.3.3.2.8.1.1.13" -> EcCurve.BRAINPOOLP512R1
                        else -> throw IllegalStateException("Unexpected curve OID $ecCurveString")
                    }
                }

                "1.3.101.110" -> EcCurve.X25519
                "1.3.101.111" -> EcCurve.X448
                "1.3.101.112" -> EcCurve.ED25519
                "1.3.101.113" -> EcCurve.ED448
                else -> throw IllegalStateException("Unexpected OID $algorithmOidString")
            }
        return curve
    }

    actual val ecPublicKey: EcPublicKey
        get() {
            val curve = getCurve(javaX509Certificate.tbsCertificate)
            return javaX509Certificate.publicKey.toEcPublicKey(curve)
        }

    actual companion object {
        @OptIn(ExperimentalEncodingApi::class)
        actual fun fromPem(pemEncoding: String): X509Cert {
            val encoded = Base64.Mime.decode(pemEncoding
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .trim())
            return X509Cert(encoded)
        }

        actual fun fromDataItem(dataItem: DataItem): X509Cert {
            return X509Cert(dataItem.asBstr)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X509Cert) return false

        return encodedCertificate.contentEquals(other.encodedCertificate)
    }

    override fun hashCode(): Int {
        return encodedCertificate.contentHashCode()
    }
}

/**
 * The Java X509 certificate from the encoded certificate data.
 */
val X509Cert.javaX509Certificate: X509Certificate
    get() {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val certBais = ByteArrayInputStream(this.encodedCertificate)
            return cf.generateCertificate(certBais) as X509Certificate
        } catch (e: CertificateException) {
            throw IllegalStateException("Error decoding certificate blob", e)
        }
    }

/**
 * Options for [Crypto.createX509v3Certificate] function.
 */
enum class X509CertificateCreateOption {
    /**
     * Include the Subject Key Identifier extension as per RFC 5280 section 4.2.1.2.
     *
     * The extension will be marked as non-critical.
     */
    INCLUDE_SUBJECT_KEY_IDENTIFIER,

    /**
     * Set the Authority Key Identifier with keyIdentifier set to the same value as the
     * Subject Key Identifier.
     *
     * This option is only meaningful when creating a self-signed certificate.
     *
     * The extension will be marked as non-critical.
     */
    INCLUDE_AUTHORITY_KEY_IDENTIFIER_AS_SUBJECT_KEY_IDENTIFIER,

    /**
     * Set the Authority Key Identifier with keyIdentifier set to the same value as the
     * Subject Key Identifier in the given `sigingKeyCertificate`.
     */
    INCLUDE_AUTHORITY_KEY_IDENTIFIER_FROM_SIGNING_KEY_CERTIFICATE,
}

/**
 * Data for a X509 extension
 *
 * @param oid the OID of the extension.
 * @param isCritical criticality.
 * @param payload the payload of the extension.
 */
data class X509CertificateExtension(
    val oid: String,
    val isCritical: Boolean,
    val payload: ByteArray
)

/**
 * Creates a certificate for a public key.
 *
 * @param publicKey the key to create a certificate for.
 * @param signingKey the key to sign the certificate.
 * @param signingKeyCertificate the certificate for the signing key, if available.
 * @param signatureAlgorithm the signature algorithm to use.
 * @param serial the serial, must be a number.
 * @param subject the subject string.
 * @param issuer the issuer string.
 * @param validFrom when the certificate should be valid from.
 * @param validUntil when the certificate should be valid until.
 * @param options one or more options from the [CreateCertificateOption] enumeration.
 * @param additionalExtensions additional extensions to put into the certificate.
 */
fun X509Cert.Companion.create(publicKey: EcPublicKey,
                              signingKey: EcPrivateKey,
                              signingKeyCertificate: X509Cert?,
                              signatureAlgorithm: Algorithm,
                              serial: String,
                              subject: String,
                              issuer: String,
                              validFrom: Instant,
                              validUntil: Instant,
                              options: Set<X509CertificateCreateOption>,
                              additionalExtensions: List<X509CertificateExtension>): X509Cert {
    val signatureAlgorithmString = when (signatureAlgorithm) {
        Algorithm.ES256 -> "SHA256withECDSA"
        Algorithm.ES384 -> "SHA384withECDSA"
        Algorithm.ES512 -> "SHA512withECDSA"
        Algorithm.EDDSA -> {
            when (signingKey.curve) {
                EcCurve.ED25519 -> "Ed25519"
                EcCurve.ED448 -> "Ed448"
                else -> throw IllegalArgumentException(
                    "ALGORITHM_EDDSA can only be used with Ed25519 and Ed448"
                )
            }
        }

        else -> throw IllegalArgumentException("Algorithm cannot be used for signing")
    }
    val certSigningKeyJava = signingKey.javaPrivateKey

    val publicKeyJava = publicKey.javaPublicKey
    val certBuilder = JcaX509v3CertificateBuilder(
        X500Name(issuer),
        BigInteger(serial),
        Date(validFrom.toEpochMilliseconds()),
        Date(validUntil.toEpochMilliseconds()),
        X500Name(subject),
        publicKeyJava
    )
    if (options.contains(X509CertificateCreateOption.INCLUDE_SUBJECT_KEY_IDENTIFIER)) {
        certBuilder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            JcaX509ExtensionUtils().createSubjectKeyIdentifier(publicKeyJava)
        )
    }
    if (options.contains(X509CertificateCreateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_AS_SUBJECT_KEY_IDENTIFIER)) {
        certBuilder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            JcaX509ExtensionUtils().createAuthorityKeyIdentifier(publicKeyJava)
        )
    }
    if (options.contains(
            X509CertificateCreateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_FROM_SIGNING_KEY_CERTIFICATE)) {
        check(signingKeyCertificate != null)
        val signerCert = signingKeyCertificate.javaX509Certificate
        val encoded = signerCert.getExtensionValue(Extension.subjectKeyIdentifier.toString())
        val subjectKeyIdentifier = SubjectKeyIdentifier.getInstance(ASN1OctetString.getInstance(encoded).octets)
        certBuilder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            AuthorityKeyIdentifier(subjectKeyIdentifier.keyIdentifier)
        )
    }
    additionalExtensions.forEach { extension ->
        certBuilder.addExtension(
            ASN1ObjectIdentifier(extension.oid),
            extension.isCritical,
            extension.payload
        )
    }
    val signer = JcaContentSignerBuilder(signatureAlgorithmString).build(certSigningKeyJava)
    val encodedCert: ByteArray = certBuilder.build(signer).getEncoded()
    val cf = CertificateFactory.getInstance("X.509")
    val bais = ByteArrayInputStream(encodedCert)
    val x509cert = cf.generateCertificate(bais) as X509Certificate
    return X509Cert(x509cert.encoded)
}