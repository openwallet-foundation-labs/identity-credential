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

import java.security.cert.CertificateException
import java.security.cert.PKIXCertPathChecker
import java.security.cert.X509Certificate

/**
 * This class is used for the verification of a certificate
 * chain.
 *
 * The user of the class can add trust roots using method
 * [addCertificate]. At this moment certificates of type
 * [X509Certificate] are supported.
 *
 * The Subject Key Identifier (extension 2.5.29.14 in the
 * [X509Certificate]) is used as the primary key / unique
 * identifier of the root CA certificate. In the verification
 * of the chain this will be matched with the Authority Key
 * Identifier (extension 2.5.29.35) of the certificate issued
 * by this root CA.
 */
class TrustManager() {

    private val certificates: MutableMap<String, X509Certificate> = mutableMapOf()

    /**
     * Nested class containing the result of the verification
     * of a certificate chain.
     */
    class TrustResult(
        var isTrusted: Boolean,
        var trustChain: List<X509Certificate> = emptyList(),
        var error: Throwable? = null
    )

    /**
     * Add a certificate to the [TrustManager].
     */
    fun addCertificate(certificate: X509Certificate) {
        if (certificateExists(certificate)) {
            throw Exception("Certificate already exists")
        }
        val key = TrustManagerUtil.getSubjectKeyIdentifier(certificate)
        certificates[key] = certificate
    }

    /**
     * Check that a certificate exists.
     */
    fun certificateExists(certificate: X509Certificate): Boolean {
        val key = TrustManagerUtil.getSubjectKeyIdentifier(certificate)
        return certificates[key] != null
    }

    /**
     * Get all the certificates in the [TrustManager].
     */
    fun getAllCertificates(): List<X509Certificate> {
        return certificates.values.toList()
    }

    /**
     * Remove a certificate from the [TrustManager].
     */
    fun removeCertificate(certificate: X509Certificate) {
        val key = TrustManagerUtil.getSubjectKeyIdentifier(certificate)
        certificates.remove(key)
    }

    /**
     * Verify a certificate chain (without the self-signed
     * root certificate) with the possibility of custom
     * validations on the certificates ([customValidators]),
     * for instance the country code in certificate chain
     * of the mDL, like implemented in the CountryValidator
     * in the reader app.
     *
     * @param [chain] the certificate chain without the
     * self-signed root certificate
     * @param [customValidators] optional parameter with
     * custom validators
     * @return [TrustResult] a class that returns a verdict
     * [TrustResult.isTrusted], optionally [TrustResult.trustChain]:
     * the complete certificate chain, including the root
     * certificate and optionally [TrustResult.error]: an
     * error message when the certificate chain is not trusted.
     */
    fun verify(
        chain: List<X509Certificate>,
        customValidators: List<PKIXCertPathChecker> = emptyList()
    ): TrustResult {
        try {
            val trustedRoot = findTrustedRoot(chain)
            val completeChain = chain.toMutableList().plus(trustedRoot)
            try {
                validateCertificationTrustPath(completeChain, customValidators)
                return TrustResult(
                    isTrusted = true,
                    trustChain = completeChain
                )
            } catch (e: Throwable) {
                // there are validation errors, but the trust chain could be built.
                return TrustResult(
                    isTrusted = false,
                    trustChain = completeChain,
                    error = e
                )
            }
        } catch (e: Throwable) {
            // no trusted root could be found.
            return TrustResult(
                isTrusted = false,
                error = e
            )
        }

    }

    /**
     * Find the trusted root of a certificate chain.
     */
    private fun findTrustedRoot(chain: List<X509Certificate>): X509Certificate {
        chain.forEach { cert ->
            run {
                val key = TrustManagerUtil.getAuthorityKeyIdentifier(cert)
                if (certificates.containsKey(key)) {
                    return certificates[key]!!
                }
            }
        }
        throw CertificateException("Trusted root certificate could not be found")
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
        if (caCertificate != null) {
            // check the signature of the self signed root certificate
            TrustManagerUtil.verifySignature(caCertificate, caCertificate)
        }
    }
}