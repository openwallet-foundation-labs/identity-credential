package com.android.mdl.appreader.readercertgen

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.CertIOException
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.IOException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Optional

object CertificateGenerator {
    private const val CRITICAL = true
    private const val NOT_CRITICAL = false
    @JvmStatic
    @Throws(CertIOException::class, CertificateException::class, OperatorCreationException::class)
    fun generateCertificate(
        data: DataMaterial,
        certMaterial: CertificateMaterial,
        keyMaterial: KeyMaterial
    ): X509Certificate {
        val issuerCert: Optional<X509Certificate> = keyMaterial.issuerCertificate
        val subjectDN = X500Name(data.subjectDN)
        // doesn't work, get's reordered
        // issuerCert.isPresent() ? new X500Name(issuerCert.get().getSubjectX500Principal().getName()) : subjectDN;
        val issuerDN = X500Name(data.issuerDN)
        val contentSigner =
            JcaContentSignerBuilder(keyMaterial.signingAlgorithm).build(keyMaterial.signingKey)
        val certBuilder = JcaX509v3CertificateBuilder(
            issuerDN,
            certMaterial.serialNumber,
            certMaterial.startDate, certMaterial.endDate,
            subjectDN,
            keyMaterial.publicKey
        )


        // Extensions --------------------------
        val jcaX509ExtensionUtils: JcaX509ExtensionUtils
        jcaX509ExtensionUtils = try {
            JcaX509ExtensionUtils()
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
        if (issuerCert.isPresent) {
            try {
                // adds 3 more fields, not present in other cert
                //				AuthorityKeyIdentifier authorityKeyIdentifier = jcaX509ExtensionUtils.createAuthorityKeyIdentifier(issuerCert.get());
                val authorityKeyIdentifier =
                    jcaX509ExtensionUtils.createAuthorityKeyIdentifier(issuerCert.get().publicKey)
                certBuilder.addExtension(
                    Extension.authorityKeyIdentifier,
                    NOT_CRITICAL,
                    authorityKeyIdentifier
                )
            } catch (e: IOException) { // CertificateEncodingException |
                throw RuntimeException(e)
            }
        }
        val subjectKeyIdentifier: SubjectKeyIdentifier =
            jcaX509ExtensionUtils.createSubjectKeyIdentifier(keyMaterial.publicKey)
        certBuilder.addExtension(Extension.subjectKeyIdentifier, NOT_CRITICAL, subjectKeyIdentifier)
        val keyUsage = KeyUsage(certMaterial.keyUsage)
        certBuilder.addExtension(Extension.keyUsage, CRITICAL, keyUsage)

        // IssuerAlternativeName
        val issuerAlternativeName: Optional<String> = data.issuerAlternativeName
        if (issuerAlternativeName.isPresent) {
            val issuerAltName = GeneralNames(
                GeneralName(
                    GeneralName.uniformResourceIdentifier,
                    issuerAlternativeName.get()
                )
            )
            certBuilder.addExtension(Extension.issuerAlternativeName, NOT_CRITICAL, issuerAltName)
        }

        // Basic Constraints
        val pathLengthConstraint: Int = certMaterial.pathLengthConstraint
        if (pathLengthConstraint != CertificateMaterial.PATHLENGTH_NOT_A_CA) {
            // TODO doesn't work for certificate chains != 2 in size
            val basicConstraints = BasicConstraints(pathLengthConstraint)
            certBuilder.addExtension(Extension.basicConstraints, CRITICAL, basicConstraints)
        }
        val extendedKeyUsage: Optional<String> = certMaterial.extendedKeyUsage
        if (extendedKeyUsage.isPresent) {
            val keyPurpose = KeyPurposeId.getInstance(ASN1ObjectIdentifier(extendedKeyUsage.get()))
            val extKeyUsage = ExtendedKeyUsage(arrayOf(keyPurpose))
            certBuilder.addExtension(Extension.extendedKeyUsage, CRITICAL, extKeyUsage)
        }

        // DEBUG setProvider(bcProvider) removed before getCertificate
        return JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner))
    }
}