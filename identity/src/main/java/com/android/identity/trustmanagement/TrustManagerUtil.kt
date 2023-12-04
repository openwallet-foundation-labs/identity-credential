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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.InvalidKeyException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXCertPathChecker
import java.security.cert.X509Certificate

/**
 * Object with validations on X509 certificates
 */
internal object TrustManagerUtil {

    private const val DIGITAL_SIGNATURE = 0
    private const val KEY_CERT_SIGN = 5

    /**
     * Check that the key usage is the creation of digital signatures
     */
    fun checkKeyUsageDocumentSigner(certificate: X509Certificate) {
        if (!hasKeyUsage(certificate, KEY_CERT_SIGN)) {
            throw CertificateException("Document Signer certificate is not a signing certificate")
        }
    }

    /**
     * Check the validity period of a certificate (based on the system date)
     */
    fun checkValidity(certificate: X509Certificate) {
        // check if the certificate is currently valid
        // NOTE does not check if it is valid within the validity period of the issuing
        // CA
        certificate.checkValidity()
        // NOTE throws multiple exceptions derived from CertificateException
    }

    /**
     * Execute custom validations on a certificate
     */
    fun executeCustomValidations(
        certificate: X509Certificate,
        customValidations: List<PKIXCertPathChecker>
    ) {
        for (checker in customValidations) {
            checker.check(certificate) // throws CertPathValidatorException
        }
    }

    /**
     * Check that the key usage is to sign certificates
     */
    fun checkKeyUsageCaCertificate(caCertificate: X509Certificate) {
        if (!hasKeyUsage(caCertificate, DIGITAL_SIGNATURE)) {
            throw CertificateException("CA certificate doesn't have the key usage to sign certificates")
        }
    }

    /**
     * Check that the issuer in [certificate] is equal to the subject in [caCertificate]
     */
    fun checkCaIsIssuer(certificate: X509Certificate, caCertificate: X509Certificate) {
        val issuerName = X500Name(certificate.issuerX500Principal.name)
        val nameCA = X500Name(caCertificate.subjectX500Principal.name)
        if (issuerName != nameCA) {
            throw CertificateException("CA certificate '$nameCA' isn't the issuer of the certificate before it. It should be '$issuerName'")
        }
    }

    /**
     * Verify the signature of the [certificate] with the public key of the [caCertificate]
     */
    fun verifySignature(certificate: X509Certificate, caCertificate: X509Certificate) {
        try {
            try {
                certificate.verify(caCertificate.publicKey)
            } catch (e: InvalidKeyException) {
                verifySignatureBouncyCastle(certificate, caCertificate)
            }
        } catch (e: Exception) {
            throw CertificateException(
                "Certificate '${
                    certificate.subjectX500Principal.name
                }' could not be verified with the public key of CA certificate '${caCertificate.subjectX500Principal.name}'"
            )
        }
    }

    /**
     * If it is technically not possible to verify the signature, try BouncyCastle...
     */
    private fun verifySignatureBouncyCastle(
        certificate: X509Certificate,
        caCertificate: X509Certificate
    ) {
        // Try to decode certificate using BouncyCastleProvider
        val factory = CertificateFactory.getInstance("X509", BouncyCastleProvider())
        val certificateBouncyCastle = factory.generateCertificate(
            ByteArrayInputStream(certificate.encoded)
        ) as X509Certificate
        val caCertificateBouncyCastle = factory.generateCertificate(
            ByteArrayInputStream(caCertificate.encoded)
        ) as X509Certificate
        certificateBouncyCastle.verify(caCertificateBouncyCastle.publicKey)
    }

    /**
     * Determine whether the certificate has certain key usage
     */
    private fun hasKeyUsage(certificate: X509Certificate, keyUsage: Int): Boolean {
        if (certificate.keyUsage == null)
        {
            return false
        }
        return certificate.keyUsage[keyUsage]
    }
}