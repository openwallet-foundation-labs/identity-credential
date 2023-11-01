package com.android.mdl.appreader.issuerauth

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Class for the parsing, validation and storage of CA Certificates
 * Because of it's dependency of Context, this class should be used from VerifierApp.caCertificateStoreInstance
 */
class CaCertificateStore(context: Context) : Store<X509Certificate>(context) {
    override val folderName: String
        get() = "Certificates"
    override val extension: String
        get() = ".crt"

    /**
     * Parse a byte array to an X509Certificate
     */
    override fun parse(content: ByteArray): X509Certificate {
        return CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(content)) as X509Certificate
    }

    /**
     * Determine the filename using the subject of the certificate
     */
    override fun determineFileName(item: X509Certificate): String {
        return X500Name(item.subjectX500Principal.name).toString()
    }

    /**
     * Validate that the [item] is a valid CA Certificate
     */
    override fun validate(item: X509Certificate) {
        // check the key usage is to sign certificates
        CertificateValidations.checkKeyUsageCaCertificate(item)
        // check that issuer = subject
        CertificateValidations.checkCaIsIssuer(item, item)
        // check self signed signature
        CertificateValidations.verifySignature(item, item)
    }

}