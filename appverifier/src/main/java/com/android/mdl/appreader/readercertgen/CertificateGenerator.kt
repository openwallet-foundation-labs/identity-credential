package com.android.mdl.appreader.readercertgen;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
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
import java.util.Optional;

public final class CertificateGenerator {
	private static final boolean CRITICAL = true;
	private static final boolean NOT_CRITICAL = false;

	private CertificateGenerator() {
		// avoid instantiation
	}

	static X509Certificate generateCertificate(DataMaterial data, CertificateMaterial certMaterial, KeyMaterial keyMaterial)
			throws CertIOException, CertificateException, OperatorCreationException {
		Provider bcProvider = new BouncyCastleProvider();
		Security.addProvider(bcProvider);

		Optional<X509Certificate> issuerCert = keyMaterial.issuerCertificate();

		X500Name subjectDN = new X500Name(data.subjectDN());
		// doesn't work, get's reordered
		// issuerCert.isPresent() ? new X500Name(issuerCert.get().getSubjectX500Principal().getName()) : subjectDN;
		X500Name issuerDN = new X500Name(data.issuerDN());

		ContentSigner contentSigner = new JcaContentSignerBuilder(keyMaterial.signingAlgorithm()).build(keyMaterial.signingKey());

		JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
				issuerDN,
				certMaterial.serialNumber(),
				certMaterial.startDate(), certMaterial.endDate(),
				subjectDN,
				keyMaterial.publicKey());


		// Extensions --------------------------

		JcaX509ExtensionUtils jcaX509ExtensionUtils;
		try {
			jcaX509ExtensionUtils = new JcaX509ExtensionUtils();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}


		if (issuerCert.isPresent()) {
			try {
				// adds 3 more fields, not present in other cert
				//				AuthorityKeyIdentifier authorityKeyIdentifier = jcaX509ExtensionUtils.createAuthorityKeyIdentifier(issuerCert.get());
				AuthorityKeyIdentifier authorityKeyIdentifier = jcaX509ExtensionUtils.createAuthorityKeyIdentifier(issuerCert.get().getPublicKey());
				certBuilder.addExtension(Extension.authorityKeyIdentifier, NOT_CRITICAL, authorityKeyIdentifier);
			} catch (IOException e) { // CertificateEncodingException |
				throw new RuntimeException(e);
			}
		}

		SubjectKeyIdentifier subjectKeyIdentifier = jcaX509ExtensionUtils.createSubjectKeyIdentifier(keyMaterial.publicKey());
		certBuilder.addExtension(Extension.subjectKeyIdentifier, NOT_CRITICAL, subjectKeyIdentifier);

		KeyUsage keyUsage = new KeyUsage(certMaterial.keyUsage());
		certBuilder.addExtension(Extension.keyUsage, CRITICAL, keyUsage);

		// IssuerAlternativeName
		Optional<String> issuerAlternativeName = data.issuerAlternativeName();
		if (issuerAlternativeName.isPresent()) {
			GeneralNames issuerAltName = new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier, issuerAlternativeName.get()));
			certBuilder.addExtension(Extension.issuerAlternativeName, NOT_CRITICAL, issuerAltName);
		}

		// Basic Constraints
		int pathLengthConstraint = certMaterial.pathLengthConstraint();
		if (pathLengthConstraint != CertificateMaterial.PATHLENGTH_NOT_A_CA) {
			// TODO doesn't work for certificate chains != 2 in size
			BasicConstraints basicConstraints = new BasicConstraints(pathLengthConstraint);
			certBuilder.addExtension(Extension.basicConstraints, CRITICAL, basicConstraints);
		}

		Optional<String> extendedKeyUsage = certMaterial.extendedKeyUsage();
		if (extendedKeyUsage.isPresent()) {
			KeyPurposeId keyPurpose = KeyPurposeId.getInstance(new ASN1ObjectIdentifier(extendedKeyUsage.get()));
			ExtendedKeyUsage extKeyUsage = new ExtendedKeyUsage(new KeyPurposeId[]{keyPurpose});
			certBuilder.addExtension(Extension.extendedKeyUsage, CRITICAL, extKeyUsage);
		}

		// DEBUG setProvider(bcProvider) removed before getCertificate
		return new JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner));
	}


}
