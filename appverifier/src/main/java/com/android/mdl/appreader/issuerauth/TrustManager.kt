package com.android.mdl.appreader.issuerauth

import com.android.mdl.appreader.issuerauth.vical.Vical
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.PKIXCertPathChecker
import java.security.cert.X509Certificate

/**
 * [TrustManager] class used for the verification of a certificate chain
 * Because of it's dependency of Context, this class should be used from VerifierApp.trustManagerInstance
 */
class TrustManager(
    private val getCertificates: () -> List<X509Certificate>,
    private val getVicals: () -> List<Vical>
) {
    private val certificatesForAllDocTypes: MutableMap<X500Name, X509Certificate>
    private val certificatesByDocType: MutableMap<String, MutableMap<X500Name, X509Certificate>>
    private val vicalsByDocTypeAndName: MutableMap<Pair<String, X500Name>, MutableList<Vical>>

    /**
     * Class containing the result of the verification of a certificate chain
     */
    class TrustResult(
        var isTrusted: Boolean,
        var trustChain: List<X509Certificate> = emptyList(),
        var vicals: List<Vical> = emptyList(),
        var error: String = ""
    )

    init {
        certificatesForAllDocTypes = HashMap()
        certificatesByDocType = HashMap()
        vicalsByDocTypeAndName = HashMap()
        reset()
    }

    /**
     * Reset the [TrustManager] after adding or removing CA certificates and/or vicals
     */
    fun reset() {
        certificatesForAllDocTypes.clear()
        certificatesByDocType.clear()
        vicalsByDocTypeAndName.clear()
        addCertificates()
        addVicals()
    }

    /**
     * * Verify a certificate chain (without the self-signed root certificate) by mDoc type with
     * the possibility of custom validations on the certificates (mdocAndCRLPathCheckers),
     * for instance the mDL country code
     * @param [chain] the certificate chain without the self-signed root certificate
     * @param [mdocType] optional parameter mdocType. If left blank, the certificates that are not
     * specific for any mDoc type will be used
     * @param [customValidators] optional parameter with custom validators
     * @return [TrustResult] a class that returns a verdict [TrustResult.isTrusted], optionally
     * [TrustResult.trustChain]: the complete certificate chain, including the root certificate,
     * optionally [TrustResult.vicals]: the vicals that trust the root certificate and optionally
     * [TrustResult.error]: an error message when the certificate chain is not trusted
     */
    fun verify(
        chain: List<X509Certificate>,
        mdocType: String = "",
        customValidators: List<PKIXCertPathChecker> = emptyList()
    ): TrustResult {
        val trustedRoot = findTrustedRoot(chain, mdocType)
            ?: return TrustResult(
                isTrusted = false,
                error = "Trusted root certificate could not be found"
            )
        val completeChain = chain.toMutableList().plus(trustedRoot)
        val vicalsKey = Pair(mdocType, X500Name(trustedRoot.subjectX500Principal.name))
        val vicals = vicalsByDocTypeAndName.getOrDefault(vicalsKey, emptyList())
        try {
            validateCertificationTrustPath(completeChain, customValidators)
            return TrustResult(
                isTrusted = true,
                trustChain = completeChain,
                vicals = vicals
            )
        } catch (e: Throwable) {
            return TrustResult(
                isTrusted = false,
                trustChain = completeChain,
                vicals = vicals,
                error = e.message.toString()
            )
        }
    }

    private fun findTrustedRoot(chain: List<X509Certificate>, mdocType: String): X509Certificate? {
        chain.forEach { cert ->
            run {
                val name = X500Name(cert.issuerX500Principal.name)
                // look first in the certificates for all mdoc types
                if (certificatesForAllDocTypes.containsKey(name)) {
                    return certificatesForAllDocTypes[name]
                }
                // find the certificate by mdoc type
                if (certificatesByDocType.containsKey(mdocType) &&
                    certificatesByDocType[mdocType]?.containsKey(name) == true
                ) {
                    return certificatesByDocType[mdocType]?.get(name)
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
        val leafCert = certIterator.next()
        CertificateValidations.checkKeyUsageDocumentSigner(leafCert)
        CertificateValidations.checkValidity(leafCert)
        CertificateValidations.executeCustomValidations(leafCert, customValidators)

        // Note that the signature of the trusted certificate itself is not verified even if it is self signed
        var prevCert = leafCert
        var caCert: X509Certificate
        while (certIterator.hasNext()) {
            caCert = certIterator.next()
            CertificateValidations.checkKeyUsageCaCertificate(caCert)
            CertificateValidations.checkCaIsIssuer(prevCert, caCert)
            CertificateValidations.verifySignature(prevCert, caCert)
            CertificateValidations.executeCustomValidations(caCert, customValidators)
            prevCert = caCert
        }
    }

    private fun addCertificates() {
        val certificates = getCertificates()
        certificates.forEach { cert ->
            run {
                val name = X500Name(cert.subjectX500Principal.name)
                if (!certificateExists(name)) {
                    certificatesForAllDocTypes[name] = cert
                }
            }
        }
    }

    private fun addVicals() {
        val vicals = getVicals()
        for (vical in vicals) {
            for (certificateInfo in vical.certificateInfos()) {
                val name = X500Name(certificateInfo.certificate.subjectX500Principal.name)
                for (docType in certificateInfo.docTypes.filter { !it.isEmpty() }) {

                    // add to certificatesByDocType
                    if (!certificatesByDocType.containsKey(docType)) {
                        certificatesByDocType[docType] = HashMap()
                    }
                    if(!certificatesByDocType[docType]?.containsKey(name)!!)
                    {
                        certificatesByDocType[docType]?.set(name,
                            certificateInfo.certificate
                        )
                    }
                    // add to vicalsByDocTypeAndName
                    val key = Pair(docType, name)
                    if (!vicalsByDocTypeAndName.containsKey(key)){
                        vicalsByDocTypeAndName[key] = mutableListOf()
                    }
                    vicalsByDocTypeAndName[key]?.add(vical)
                }
            }
        }
    }

    private fun certificateExists(name: X500Name): Boolean {
        if (certificatesForAllDocTypes.containsKey(name)) {
            return true
        }
        certificatesByDocType.forEach { entry ->
            if (entry.value.containsKey(name)) {
                return true
            }
        }
        return false
    }
}