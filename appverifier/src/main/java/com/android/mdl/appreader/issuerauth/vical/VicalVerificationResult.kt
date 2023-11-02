package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import org.bouncycastle.asn1.ess.OtherSigningCertificate
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate

/**
 * A COSE1 signature within ISO/IEC 18013-5 consists of a structure which consists of an algorithm specification
 * in the protected parameters, a single leaf certificate and the encapsulated data.
 *
 * This result structure consists of an error code which is always `VERIFICATION_SUCCEEDED` if
 * the signature verification was successful, or one of the other error codes if verification failed. Some implementations
 * may return more precise information than others; implementations should at least offer VERIFICATION_SUCCEEDED,
 * VERIFICATION_FAILED if the signature doesn't match and DECODING_ERROR as fallback.
 *
 * If verification was successful then the [signingCertificate] and
 * [VicalVerificationResult.content]methods should return the certificate and content, otherwise these
 * fields may return `null`.
 *
 * @author UL TS BV
 */
data class VicalVerificationResult (val code: Code, val signingCertificate: X509Certificate?, val payload: ByteString?) {
    enum class Code {
        VERIFICATION_SUCCEEDED, DECODING_ERROR, CERTIFICATE_NOT_FOUND, DATA_NOT_INCLUDED, VERIFICATION_FAILED, UNKNOWN_SIGNING_ALGORITHM, PUBLIC_KEY_DOESNT_MATCH_ALGORITHM
    }

    override fun toString(): String {
        val sha1: MessageDigest
        sha1 = try {
            MessageDigest.getInstance("SHA1")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(
                "SHA-1 should always be available, security configuration error",
                e
            )
        }
        val hashOverCertificate: String?
        hashOverCertificate = if (signingCertificate != null) {
            try {
                Hex.toHexString(
                    sha1.digest(
                        signingCertificate.encoded
                    )
                )
            } catch (e: CertificateEncodingException) {
                "[Certificate encoding failed]"
            }
        } else {
            "[No certificate available]"
        }
        var hashOverContent: String?
        if (payload != null) {
            try {
                // TODO should probably be over payload.bytes
                ByteArrayOutputStream(SHA1_HASH_OUTPUT_SIZE).use { hashOutput ->
                    val encoder = CborEncoder(DigestOutputStream(hashOutput, sha1))
                    encoder.encode(payload)
                    hashOverContent = Hex.toHexString(sha1.digest())
                }
            } catch (e: IOException) {
                throw RuntimeException("ByteArrayOutputStream should not throw an I/O exception", e)
            } catch (e: CborException) {
                hashOverContent = "[Content encoding failed]"
            }
        } else {
            hashOverContent = "[No content available]"
        }
        return String.format(
            "Verification result: %s, certificate hash: %s, VICAL hash: %s",
            code, hashOverCertificate, hashOverContent
        )
    }

    companion object {
        private const val SHA1_HASH_OUTPUT_SIZE = 20
    }
}