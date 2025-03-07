package org.multipaz.trustmanagement

import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.util.toHex
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * This class is used for the verification of a certificate chain.
 *
 * The user of the class can add trust roots using method [addTrustPoint].
 * At this moment certificates of type [X509Cert] are supported.
 *
 * The Subject Key Identifier (extension 2.5.29.14 in the [X509Cert])
 * is used as the primary key / unique identifier of the root CA certificate.
 * In the verification of the chain this will be matched with the Authority
 * Key Identifier (extension 2.5.29.35) of the certificate issued by this
 * root CA.
 */
class TrustManager {

    // Maps from the hex-encoding of SubjectKeyIdentifier
    private val certificates = mutableMapOf<String, TrustPoint>()

    /**
     * Nested class containing the result of the verification of a certificate
     * chain.
     *
     * @property isTrusted trust if a trust point was found.
     * @property trustChain the chain that was built, is `null` if [isTrusted] is `false`.
     * @property trustPoints the set of trust points that matched, is empty if [isTrusted] is `false`.
     * @property error a [Throwable] indicating if an error occurred validating the trust chain.
     */
    data class TrustResult(
        val isTrusted: Boolean,
        val trustChain: X509CertChain? = null,
        val trustPoints: List<TrustPoint> = emptyList(),
        val error: Throwable? = null
    )

    /**
     * Add a [TrustPoint] to the [TrustManager].
     */
    fun addTrustPoint(trustPoint: TrustPoint) {
        check(trustPoint.certificate.subjectKeyIdentifier != null)
        certificates[trustPoint.certificate.subjectKeyIdentifier!!.toHex()] = trustPoint
    }


    /**
     * Get all the [TrustPoint]s in the [TrustManager].
     */
    fun getAllTrustPoints(): List<TrustPoint> = certificates.values.toList()

    /**
     * Remove a [TrustPoint] from the [TrustManager].
     */
    fun removeTrustPoint(trustPoint: TrustPoint) = {
        check(trustPoint.certificate.subjectKeyIdentifier != null)
        certificates.remove(trustPoint.certificate.subjectKeyIdentifier!!.toHex())
    }

    /**
     * Verify a certificate chain (without the self-signed root certificate).
     *
     * @param [chain] the certificate chain without the self-signed root
     * certificate
     * @param [atTime] the point in time to check validity for.
     * @return [TrustResult] a class that returns a verdict
     * [TrustResult.isTrusted], optionally [TrustResult.trustPoints] the found
     * trusted root certificates with their display names and icons, optionally
     * [TrustResult.trustChain], the complete certificate chain, including the
     * root/intermediate certificate(s), and optionally [TrustResult.error]:
     * an error message when the certificate chain is not trusted.
     */
    fun verify(
        chain: List<X509Cert>,
        atTime: Instant = Clock.System.now(),
    ): TrustResult {
        // TODO: add support for customValidators similar to PKIXCertPathChecker
        try {
            val trustPoints = getAllTrustPoints(chain)
            val completeChain = chain.plus(trustPoints.map { it.certificate })
            try {
                validateCertificationTrustPath(completeChain, atTime)
                return TrustResult(
                    isTrusted = true,
                    trustPoints = trustPoints,
                    trustChain = X509CertChain(completeChain)
                )
            } catch (e: Throwable) {
                // there are validation errors, but the trust chain could be built.
                return TrustResult(
                    isTrusted = false,
                    trustPoints = trustPoints,
                    trustChain = X509CertChain(completeChain),
                    error = e
                )
            }
        } catch (e: Throwable) {
            // No CA certificate found for the passed in chain.
            //
            // However, handle the case where the passed in chain _is_ a trust point. This won't
            // happen for mdoc issuer auth (the IACA cert is never part of the chain) but can happen
            // with mdoc reader auth, especially at mDL test events where each participant
            // just submits a certificate for the key that their reader will be using.
            //
            if (chain.size == 1) {
                val trustPoint = certificates[chain[0].subjectKeyIdentifier!!.toHex()]
                if (trustPoint != null) {
                    return TrustResult(
                        isTrusted = true,
                        trustChain = X509CertChain(chain),
                        listOf(trustPoint),
                        error = null
                    )
                }
            }
            // no CA certificate could be found.
            return TrustResult(
                isTrusted = false,
                error = e
            )
        }
    }

    private fun getAllTrustPoints(chain: List<X509Cert>): List<TrustPoint> {
        val result = mutableListOf<TrustPoint>()

        // only an exception if not a single CA certificate is found
        var caCertificate: TrustPoint? = findCaCertificate(chain)
            ?: throw IllegalStateException("No trusted root certificate could not be found")
        result.add(caCertificate!!)
        while (caCertificate != null && !TrustManagerUtil.isSelfSigned(caCertificate.certificate)) {
            caCertificate = findCaCertificate(listOf(caCertificate.certificate))
            if (caCertificate != null) {
                result.add(caCertificate)
            }
        }
        return result
    }

    /**
     * Find a CA Certificate for a certificate chain.
     */
    private fun findCaCertificate(chain: List<X509Cert>): TrustPoint? {
        chain.forEach { cert ->
            cert.authorityKeyIdentifier?.toHex().let {
                if (certificates.containsKey(it)) {
                    return certificates[it]
                }
            }
        }
        return null
    }

    /**
     * Validate the certificate trust path.
     */
    private fun validateCertificationTrustPath(
        certificationTrustPath: List<X509Cert>,
        atTime: Instant
    ) {
        val certIterator = certificationTrustPath.iterator()
        val leafCertificate = certIterator.next()
        TrustManagerUtil.checkKeyUsageDocumentSigner(leafCertificate)
        TrustManagerUtil.checkValidity(leafCertificate, atTime)

        var previousCertificate = leafCertificate
        var caCertificate: X509Cert? = null
        while (certIterator.hasNext()) {
            caCertificate = certIterator.next()
            TrustManagerUtil.checkKeyUsageCaCertificate(caCertificate)
            TrustManagerUtil.checkCaIsIssuer(previousCertificate, caCertificate)
            TrustManagerUtil.verifySignature(previousCertificate, caCertificate)
            previousCertificate = caCertificate
        }
        if (caCertificate != null && TrustManagerUtil.isSelfSigned(caCertificate)) {
            // check the signature of the self signed root certificate
            TrustManagerUtil.verifySignature(caCertificate, caCertificate)
        }
    }
}
