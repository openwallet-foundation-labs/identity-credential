package com.android.mdl.appreader.trustmanagement

import com.android.identity.trustmanagement.countryCode
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.PKIXCertPathChecker
import java.security.cert.X509Certificate

/**
 * Class used to validate that the country code in the whole certificate chain is the same
 */
class CountryValidator : PKIXCertPathChecker() {
    private var previousCountryCode: String = ""

    /**
     * There is no custom initialisation of this class
     */
    override fun init(p0: Boolean) {
        // intentionally left empty
    }


    /**
     * Forward checking supported: the order of the certificate chain is not relevant for the check
     * on country code.
     */
    override fun isForwardCheckingSupported(): Boolean {
        return true
    }

    /**
     * Check the country code
     */
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

    /**
     * Extensions are not validated on country code
     */
    override fun getSupportedExtensions(): MutableSet<String> {
        return mutableSetOf()
    }
}