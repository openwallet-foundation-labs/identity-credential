package com.android.identity.crypto

import java.security.cert.X509Certificate

/**
 * Converts the certificate chain to a list of Java X.509 certificates.
 */
val X509CertChain.javaX509Certificates: List<X509Certificate>
    get() = certificates.map { certificate -> certificate.javaX509Certificate }
