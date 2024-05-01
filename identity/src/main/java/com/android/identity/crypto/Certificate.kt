package com.android.identity.crypto

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.DataItem
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * An encoded X509 certificate.
 *
 * @param encodedCertificate the bytes of the X.509 certificate.
 */
data class Certificate(
    val encodedCertificate: ByteArray,
) {
    private lateinit var parsedPublicKey: EcPublicKey

    /**
     * Gets an [Bstr] with the encoded X.509 certificate.
     */
    val toDataItem: DataItem
        get() = Bstr(encodedCertificate)

    /**
     * Encode this certificate in PEM format
     *
     * @return a PEM encoded string.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun toPem(): String {
        val sb = StringBuilder()
        sb.append("-----BEGIN CERTIFICATE-----\n")
        sb.append(Base64.Mime.encode(encodedCertificate))
        sb.append("\n-----END CERTIFICATE-----\n")
        return sb.toString()
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
                    val ecCurveString =
                        (
                            algorithmIdentifier.getObjectAt(1) as
                                ASN1ObjectIdentifier
                        ).id
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

    /**
     * Gets the public key in the certificate.
     *
     * Note: this should only be used for certificates for EC keys.
     */
    val publicKey: EcPublicKey
        get() {
            if (!this::parsedPublicKey.isInitialized) {
                val cert = javaX509Certificate
                val curve = getCurve(cert.tbsCertificate)
                parsedPublicKey = cert.publicKey.toEcPublicKey(curve)
            }
            return parsedPublicKey
        }

    /**
     * Verifies that the certificate was signed with a given EC public key.
     */
    fun verify(publicKey: EcPublicKey): Boolean =
        try {
            this.javaX509Certificate.verify(publicKey.javaPublicKey)
            true
        } catch (e: Exception) {
            false
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Certificate

        return encodedCertificate.contentEquals(other.encodedCertificate)
    }

    override fun hashCode(): Int = encodedCertificate.contentHashCode()

    companion object {
        /**
         * Creates a [Certificate] from a PEM encoded string.
         *
         * @param pemEncoding the PEM encoded string.
         * @return a new [Certificate].
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun fromPem(pemEncoding: String): Certificate {
            val encoded =
                Base64.Mime.decode(
                    pemEncoding
                        .replace("-----BEGIN CERTIFICATE-----", "")
                        .replace("-----END CERTIFICATE-----", "")
                        .trim(),
                )
            return Certificate(encoded)
        }

        /**
         * Gets a [Certificate] from a [Bstr].
         *
         * @param dataItem the data item, must be a [Bstr].
         * @return the certificate.
         */
        fun fromDataItem(dataItem: DataItem): Certificate = Certificate(dataItem.asBstr)
    }
}

// TODO: move to identity-jvm library

/**
 * The Java X509 certificate from the encoded certificate data.
 */
val Certificate.javaX509Certificate: X509Certificate
    get() {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val certBais = ByteArrayInputStream(this.encodedCertificate)
            return cf.generateCertificate(certBais) as X509Certificate
        } catch (e: CertificateException) {
            throw IllegalStateException("Error decoding certificate blob", e)
        }
    }
