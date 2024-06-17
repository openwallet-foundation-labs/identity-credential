package com.android.identity.crypto

/**
 * Converts the certificate chain to a list of Java X.509 certificates.
 */
val X509CertificateChain.javaX509Certificates: List<java.security.cert.X509Certificate>
    get() = certificates.map { certificate -> certificate.javaX509Certificate }
