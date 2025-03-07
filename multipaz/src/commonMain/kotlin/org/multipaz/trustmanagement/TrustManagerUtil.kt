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
package org.multipaz.trustmanagement

import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509KeyUsage
import kotlinx.datetime.Instant

/**
 * Object with utility functions for the TrustManager.
 */
internal object TrustManagerUtil {
    /**
     * Check whether a certificate is self-signed
     */
    fun isSelfSigned(certificate: X509Cert): Boolean =
        certificate.issuer == certificate.subject

    /**
     * Check that the key usage is the creation of digital signatures.
     */
    fun checkKeyUsageDocumentSigner(certificate: X509Cert) {
        check(certificate.keyUsage.contains(X509KeyUsage.DIGITAL_SIGNATURE)) {
            "Document Signer certificate is not a signing certificate"
        }
    }

    /**
     * Check the validity period of a certificate (based on the system date).
     */
    fun checkValidity(
        certificate: X509Cert,
        atTime: Instant
    ) {
        // check if the certificate is currently valid
        // NOTE does not check if it is valid within the validity period of
        // the issuing CA
        check(atTime >= certificate.validityNotBefore) {
            "Certificate is not yet valid"
        }
        check(atTime <= certificate.validityNotAfter) {
            "Certificate is no longer valid"
        }
    }

    /**
     * Check that the key usage is to sign certificates.
     */
    fun checkKeyUsageCaCertificate(caCertificate: X509Cert) {
        check(caCertificate.keyUsage.contains(X509KeyUsage.KEY_CERT_SIGN)) {
            "CA certificate doesn't have the key usage to sign certificates"
        }
    }

    /**
     * Check that the issuer in [certificate] is equal to the subject in
     * [caCertificate].
     */
    fun checkCaIsIssuer(certificate: X509Cert, caCertificate: X509Cert) {
        val issuerName = certificate.issuer.name
        val nameCA = caCertificate.subject.name
        if (issuerName != nameCA) {
            throw IllegalStateException("CA certificate '$nameCA' isn't the issuer of the certificate before it. It should be '$issuerName'")
        }
    }

    /**
     * Verify the signature of the [certificate] with the public key of the
     * [caCertificate].
     */
    fun verifySignature(certificate: X509Cert, caCertificate: X509Cert) =
        try {
            certificate.verify(caCertificate.ecPublicKey)
        } catch (e: Throwable) {
            throw IllegalStateException(
                "Certificate '${certificate.subject}' could not be verified with the public key of CA certificate '${caCertificate.subject}'"
            )
        }
}