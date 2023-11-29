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

import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.CertificateException
import java.security.cert.PKIXCertPathChecker
import java.security.cert.X509Certificate

/**
 * This class is used for the verification of a certificate chain
 */
class TrustManager() {

    private val certificates: MutableMap<X500Name, X509Certificate> = mutableMapOf()

    /**
     * Nested class containing the result of the verification of a certificate chain
     */
    class TrustResult(
        var isTrusted: Boolean,
        var trustChain: List<X509Certificate> = emptyList(),
        var error: Throwable? = null
    )

    /**
     * Add a certificate to the [TrustManager]
     */
    fun addCertificate(certificate: X509Certificate) {
        if (certificateExists(certificate)) {
            throw Exception("Certificate already exists")
        }
        val name = X500Name(certificate.subjectX500Principal.name)
        certificates[name] = certificate
    }

    /**
     * Check that a certificate exists
     */
    fun certificateExists(certificate: X509Certificate): Boolean {
        val name = X500Name(certificate.subjectX500Principal.name)
        return certificates[name] != null
    }

    /**
     * Get all the certificates in the [TrustManager]
     */
    fun getAllCertificates(): List<X509Certificate> {
        return certificates.values.toList()
    }

    /**
     * Remove a certificate from the [TrustManager]
     */
    fun removeCertificate(certificate: X509Certificate) {
        val name = X500Name(certificate.subjectX500Principal.name)
        certificates.remove(name)
    }

    /**
     * Verify a certificate chain (without the self-signed root certificate) with
     * the possibility of custom validations on the certificates ([customValidators]),
     * for instance the country code in certificate chain of the mDL, like implemented in the
     * CountryValidator in the reader app
     *
     * @param [chain] the certificate chain without the self-signed root certificate
     * @param [customValidators] optional parameter with custom validators
     * @return [TrustResult] a class that returns a verdict [TrustResult.isTrusted], optionally
     * [TrustResult.trustChain]: the complete certificate chain, including the root certificate and
     * optionally [TrustResult.error]: an error message when the certificate chain is not trusted
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
                // validation errors, but the trust chain could be built
                return TrustResult(
                    isTrusted = false,
                    trustChain = completeChain,
                    error = e
                )
            }
        } catch (e: Throwable) {
            // no trusted root found
            return TrustResult(
                isTrusted = false,
                error = e
            )
        }

    }

    /**
     * Find the trusted root of a certificate chain
     */
    private fun findTrustedRoot(chain: List<X509Certificate>): X509Certificate {
        chain.forEach { cert ->
            run {
                val name = X500Name(cert.issuerX500Principal.name)
                if (certificates.containsKey(name)) {
                    return certificates[name]!!
                }
            }
        }
        throw CertificateException("Trusted root certificate could not be found")
    }

    /**
     * Validate the certificate trust path
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

        // Note that the signature of the trusted certificate itself is not verified even if it is self signed
        var previousCertificate = leafCertificate
        var caCertificate: X509Certificate
        while (certIterator.hasNext()) {
            caCertificate = certIterator.next()
            TrustManagerUtil.checkKeyUsageCaCertificate(caCertificate)
            TrustManagerUtil.checkCaIsIssuer(previousCertificate, caCertificate)
            TrustManagerUtil.verifySignature(previousCertificate, caCertificate)
            TrustManagerUtil.executeCustomValidations(caCertificate, customValidators)
            previousCertificate = caCertificate
        }
    }
}