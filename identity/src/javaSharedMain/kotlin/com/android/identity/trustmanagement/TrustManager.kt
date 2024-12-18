/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.identity.trustmanagement

import com.android.identity.crypto.javaX509Certificate
import java.security.cert.CertificateException
import java.security.cert.PKIXCertPathChecker
import java.security.cert.X509Certificate

/**
 * This class is used for the verification of a certificate chain.
 *
 * The user of the class can add trust roots using method [addTrustPoint].
 * At this moment certificates of type [X509Certificate] are supported.
 *
 * The Subject Key Identifier (extension 2.5.29.14 in the [X509Certificate])
 * is used as the primary key / unique identifier of the root CA certificate.
 * In the verification of the chain this will be matched with the Authority
 * Key Identifier (extension 2.5.29.35) of the certificate issued by this
 * root CA.
 */
class TrustManager {

    private val certificates = mutableMapOf<String, TrustPoint>()

    /**
     * Nested class containing the result of the verification of a certificate
     * chain.
     */
    class TrustResult(
        var isTrusted: Boolean,
        var trustChain: List<X509Certificate> = listOf(),
        var trustPoints: List<TrustPoint> = listOf(),
        var error: Throwable? = null
    )

    /**
     * Add a [TrustPoint] to the [TrustManager].
     */
    fun addTrustPoint(trustPoint: TrustPoint) =
        TrustManagerUtil.getSubjectKeyIdentifier(trustPoint.certificate.javaX509Certificate).also { key ->
            if (key.isNotEmpty()) {
                certificates[key] = trustPoint
            }
        }


    /**
     * Get all the [TrustPoint]s in the [TrustManager].
     */
    fun getAllTrustPoints(): List<TrustPoint> = certificates.values.toList()

    /**
     * Remove a [TrustPoint] from the [TrustManager].
     */
    fun removeTrustPoint(trustPoint: TrustPoint) =
        TrustManagerUtil.getSubjectKeyIdentifier(trustPoint.certificate.javaX509Certificate).also { key ->
            certificates.remove(key)
        }

    /**
     * Verify a certificate chain (without the self-signed root certificate)
     * with the possibility of custom validations on the certificates
     * ([customValidators]), for instance the country code in certificate chain
     * of the mDL, like implemented in the CountryValidator in the reader app.
     *
     * @param [chain] the certificate chain without the self-signed root
     * certificate
     * @param [customValidators] optional parameter with custom validators
     * @return [TrustResult] a class that returns a verdict
     * [TrustResult.isTrusted], optionally [TrustResult.trustPoints] the found
     * trusted root certificates with their display names and icons, optionally
     * [TrustResult.trustChain], the complete certificate chain, including the
     * root/intermediate certificate(s), and optionally [TrustResult.error]:
     * an error message when the certificate chain is not trusted.
     */
    fun verify(
        chain: List<X509Certificate>,
        customValidators: List<PKIXCertPathChecker> = emptyList()
    ): TrustResult {
        try {
            val trustPoints = getAllTrustPoints(chain)
            val completeChain = chain.plus(trustPoints.map { it.certificate.javaX509Certificate })
            try {
                validateCertificationTrustPath(completeChain, customValidators)
                return TrustResult(
                    isTrusted = true,
                    trustPoints = trustPoints,
                    trustChain = completeChain
                )
            } catch (e: Throwable) {
                // there are validation errors, but the trust chain could be built.
                return TrustResult(
                    isTrusted = false,
                    trustPoints = trustPoints,
                    trustChain = completeChain,
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
                val trustPoint = certificates[TrustManagerUtil.getSubjectKeyIdentifier(chain[0])]
                if (trustPoint != null) {
                    return TrustResult(
                        isTrusted = true,
                        trustChain = chain,
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

    private fun getAllTrustPoints(chain: List<X509Certificate>): List<TrustPoint> {
        val result = mutableListOf<TrustPoint>()

        // only an exception if not a single CA certificate is found
        var caCertificate: TrustPoint? = findCaCertificate(chain)
            ?: throw CertificateException("No trusted root certificate could not be found")
        result.add(caCertificate!!)
        while (caCertificate != null && !TrustManagerUtil.isSelfSigned(caCertificate.certificate.javaX509Certificate)) {
            caCertificate = findCaCertificate(listOf(caCertificate.certificate.javaX509Certificate))
            if (caCertificate != null) {
                result.add(caCertificate)
            }
        }
        return result
    }

    /**
     * Find a CA Certificate for a certificate chain.
     */
    private fun findCaCertificate(chain: List<X509Certificate>): TrustPoint? {
        chain.forEach { cert ->
            TrustManagerUtil.getAuthorityKeyIdentifier(cert).also { key ->
                // only certificates with an Authority Key Identifier extension will be matched
                if (key.isNotEmpty() && certificates.containsKey(key)) {
                    return certificates[key]
                }
            }
        }
        return null
    }

    /**
     * Validate the certificate trust path.
     */
    private fun validateCertificationTrustPath(
        certificationTrustPath: List<X509Certificate>,
        customValidators: List<PKIXCertPathChecker>
    ) {
        val certIterator = certificationTrustPath.iterator()
        val leafCertificate = certIterator.next()
        TrustManagerUtil.checkKeyUsageDocumentSigner(leafCertificate)
        TrustManagerUtil.checkValidity(leafCertificate)
        TrustManagerUtil.executeCustomValidations(leafCertificate, customValidators)

        var previousCertificate = leafCertificate
        var caCertificate: X509Certificate? = null
        while (certIterator.hasNext()) {
            caCertificate = certIterator.next()
            TrustManagerUtil.checkKeyUsageCaCertificate(caCertificate)
            TrustManagerUtil.checkCaIsIssuer(previousCertificate, caCertificate)
            TrustManagerUtil.verifySignature(previousCertificate, caCertificate)
            TrustManagerUtil.executeCustomValidations(caCertificate, customValidators)
            previousCertificate = caCertificate
        }
        if (caCertificate != null && TrustManagerUtil.isSelfSigned(caCertificate)) {
            // check the signature of the self signed root certificate
            TrustManagerUtil.verifySignature(caCertificate, caCertificate)
        }
    }
}