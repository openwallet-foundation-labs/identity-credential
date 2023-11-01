package com.android.mdl.appreader.issuerauth

import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.PKIXCertPathChecker
import java.security.cert.X509Certificate

class CountryValidator : PKIXCertPathChecker() {
    private var previousCountryCode: String = ""
    override fun init(p0: Boolean) {
        // intentionally left empty
    }

    override fun isForwardCheckingSupported(): Boolean {
        return true
    }

    override fun check(certificate: Certificate?, state: MutableCollection<String>?) {
        if (certificate is X509Certificate) {
            val countryCode = certificate.subjectX500Principal.countryCode("")
            if (countryCode.isBlank()) {
                throw CertificateException("Country code is not present in certificate " + certificate.subjectX500Principal.name)
            }
            if (previousCountryCode.isNotBlank() && previousCountryCode.uppercase() != countryCode.uppercase()) {
                throw CertificateException("There are different country codes in the certificate chain: $previousCountryCode and $countryCode")
            } else {
                previousCountryCode = countryCode
            }
        }
    }

    override fun getSupportedExtensions(): MutableSet<String> {
        return mutableSetOf()
    }
}