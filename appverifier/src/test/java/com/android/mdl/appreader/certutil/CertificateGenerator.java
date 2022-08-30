package com.android.mdl.appreader.certutil;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.PolicyQualifierInfo;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

public final class CertificateGenerator {
    private static final boolean CRITICAL = true;
    private static final boolean NOT_CRITICAL = false;

    private CertificateGenerator() {
        // avoid instantiation
    }

    public static X509Certificate generateCertificate(DataMaterial data, CertificateMaterial certMaterial,
            KeyMaterial keyMaterial)
            throws CertIOException, CertificateException, OperatorCreationException {
        Provider bcProvider = new BouncyCastleProvider();
        Security.addProvider(bcProvider);

        Optional<X509Certificate> issuerCert = keyMaterial.issuerCertificate();

        X500Name subjectDN = new X500Name(data.subjectDN());
        
        // doesn't work, X500 name get's reordered (!)
        // issuerCert.isPresent() ? new X500Name(issuerCert.get().getSubjectX500Principal().getName()) : subjectDN;
        X500Name issuerDN = new X500Name(data.issuerDN());

        ContentSigner contentSigner = new JcaContentSignerBuilder(keyMaterial.signingAlgorithm())
                .build(keyMaterial.signingKey());

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuerDN,
                certMaterial.serialNumber(),
                certMaterial.startDate(), certMaterial.endDate(),
                subjectDN,
                keyMaterial.publicKey());

        // --- Extension Utilities required for key identifiers ---

        JcaX509ExtensionUtils jcaX509ExtensionUtils;
        try {
            jcaX509ExtensionUtils = new JcaX509ExtensionUtils();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        // --- Authority Key Identifier ---
        if (issuerCert.isPresent()) {
            try {
                AuthorityKeyIdentifier authorityKeyIdentifier = jcaX509ExtensionUtils
                        .createAuthorityKeyIdentifier(issuerCert.get().getPublicKey());
                certBuilder.addExtension(Extension.authorityKeyIdentifier, NOT_CRITICAL, authorityKeyIdentifier);
            } catch (IOException e) { // CertificateEncodingException | 
                throw new RuntimeException(e);
            }
        }

        // --- Subject Key Identifier ---
        SubjectKeyIdentifier subjectKeyIdentifier = jcaX509ExtensionUtils
                .createSubjectKeyIdentifier(keyMaterial.publicKey());
        certBuilder.addExtension(Extension.subjectKeyIdentifier, NOT_CRITICAL, subjectKeyIdentifier);

        // --- Key Usage ---
        KeyUsage keyUsage = new KeyUsage(certMaterial.keyUsage());
        certBuilder.addExtension(Extension.keyUsage, CRITICAL, keyUsage);

        // --- Issue Alternative Name ---
        Optional<String> issuerAlternativeName = data.issuerAlternativeName();
        if (issuerAlternativeName.isPresent()) {
            GeneralNames issuerAltName = new GeneralNames(
                    new GeneralName(GeneralName.uniformResourceIdentifier, issuerAlternativeName.get()));
            certBuilder.addExtension(Extension.issuerAlternativeName, NOT_CRITICAL, issuerAltName);
        }

        // --- Basic Constraints ---
        Optional<Integer> pathLengthConstraint = certMaterial.pathLengthConstraint();
        if (pathLengthConstraint.isPresent()) {
            // NOTE may not work for certificate chains != 2 in size
            BasicConstraints basicConstraints = new BasicConstraints(pathLengthConstraint.get());
            certBuilder.addExtension(Extension.basicConstraints, CRITICAL, basicConstraints);
        }

        // --- Extended Key Usage ---
        List<String> extendedKeyUsage = certMaterial.extendedKeyUsage();
        for (String extendedKeyUsageString : extendedKeyUsage) {
            KeyPurposeId keyPurpose = KeyPurposeId.getInstance(new ASN1ObjectIdentifier(extendedKeyUsageString));
            ExtendedKeyUsage extKeyUsage = new ExtendedKeyUsage(new KeyPurposeId[] { keyPurpose });
            certBuilder.addExtension(Extension.extendedKeyUsage, certMaterial.extendedKeyUsageCritical(), extKeyUsage);
        }

        // --- Policies ---
        List<String> policyOids = certMaterial.criticalPolicyOids();
        for (String policyOid : policyOids) {

            ASN1ObjectIdentifier policyOidEnc = new ASN1ObjectIdentifier(policyOid);
            PolicyQualifierInfo pqInfo = new PolicyQualifierInfo("aaa.bbb"); // the value you want
            PolicyInformation policyInfo = new PolicyInformation(policyOidEnc, new DERSequence(pqInfo));
            CertificatePolicies policies = new CertificatePolicies(policyInfo);

            certBuilder.addExtension(Extension.certificatePolicies, CRITICAL, policies);
        }
        
        // NOTE variable mainly for debugging purposes 
        return new JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner));
    }

}
