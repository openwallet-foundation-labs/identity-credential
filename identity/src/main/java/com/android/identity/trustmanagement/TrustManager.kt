package com.android.identity.trustmanagement

import com.android.identity.storage.StorageEngine
import org.bouncycastle.asn1.x500.X500Name
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.PKIXCertPathChecker
import java.security.cert.X509Certificate

/**
 * [TrustManager] class used for the verification of a certificate chain
 */
class TrustManager(val certificateStorage: StorageEngine) {

    private val certificates: MutableMap<X500Name, X509Certificate>

    /**
     * Class containing the result of the verification of a certificate chain
     */
    class TrustResult(
        var isTrusted: Boolean,
        var trustChain: List<X509Certificate> = emptyList(),
        var error: String = ""
    )

    init {
        certificates = getAllCertificates().associateBy { X500Name(it.subjectX500Principal.name) }
            .toMutableMap()
    }

    fun saveCertificate(certificateBytes: ByteArray) {
        val certificate = parseCertificate(certificateBytes)
        if (certificateExists(certificate)) {
            throw Exception("File already exists")
        }
        val name = X500Name(certificate.subjectX500Principal.name)
        certificateStorage.put(name.toString(), certificate.encoded)
        certificates[name] = certificate
    }

    fun certificateExists(certificate: X509Certificate): Boolean{
        val name = X500Name(certificate.subjectX500Principal.name)
        return certificateStorage.get(name.toString()) != null
    }

    fun getAllCertificates(): List<X509Certificate> {
        return certificateStorage.enumerate().map {
            parseCertificate(certificateStorage.get(it)!!)
        }
    }

    fun deleteCertificate(certificate: X509Certificate){
        val name = X500Name(certificate.subjectX500Principal.name)
        certificateStorage.delete(name.toString())
        certificates.remove(name)
    }

    /**
     * * Verify a certificate chain (without the self-signed root certificate) by mDoc type with
     * the possibility of custom validations on the certificates (mdocAndCRLPathCheckers),
     * for instance the mDL country code
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
        val trustedRoot = findTrustedRoot(chain)
            ?: return TrustResult(
                isTrusted = false,
                error = "Trusted root certificate could not be found"
            )
        val completeChain = chain.toMutableList().plus(trustedRoot)
        try {
            validateCertificationTrustPath(completeChain, customValidators)
            return TrustResult(
                isTrusted = true,
                trustChain = completeChain
            )
        } catch (e: Throwable) {
            return TrustResult(
                isTrusted = false,
                trustChain = completeChain,
                error = e.message.toString()
            )
        }
    }

    private fun findTrustedRoot(chain: List<X509Certificate>): X509Certificate? {
        chain.forEach { cert ->
            run {
                val name = X500Name(cert.issuerX500Principal.name)
                if (certificates.containsKey(name)) {
                    return certificates[name]
                }
            }
        }
        return null
    }

    private fun validateCertificationTrustPath(
        certificationTrustPath: List<X509Certificate>,
        customValidators: List<PKIXCertPathChecker>
    ) {
        val certIterator = certificationTrustPath.iterator()
        val leafCertificate = certIterator.next()
        CertificateValidations.checkKeyUsageDocumentSigner(leafCertificate)
        CertificateValidations.checkValidity(leafCertificate)
        CertificateValidations.executeCustomValidations(leafCertificate, customValidators)

        // Note that the signature of the trusted certificate itself is not verified even if it is self signed
        var previousCertificate = leafCertificate
        var caCertificate: X509Certificate
        while (certIterator.hasNext()) {
            caCertificate = certIterator.next()
            CertificateValidations.checkKeyUsageCaCertificate(caCertificate)
            CertificateValidations.checkCaIsIssuer(previousCertificate, caCertificate)
            CertificateValidations.verifySignature(previousCertificate, caCertificate)
            CertificateValidations.executeCustomValidations(caCertificate, customValidators)
            previousCertificate = caCertificate
        }
    }


    private fun parseCertificate(certificateBytes: ByteArray): X509Certificate {
        return CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
    }
}