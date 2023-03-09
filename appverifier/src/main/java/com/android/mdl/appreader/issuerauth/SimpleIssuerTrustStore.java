package com.android.mdl.appreader.issuerauth;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Trust manager that verifies certificate chains starting with a DS certificate, possibly followed by CA certificates.
 * This trust manager is non-specific to mDL in the sense that it doesn't validate the country and optional state according to the rules
 * set in ISO/IEC 18013-5:2021.
 */
public class SimpleIssuerTrustStore implements IssuerTrustStore {

	private static final int DIGITAL_SIGNATURE = 0;
	private static final int KEY_CERT_SIGN = 5;

	private Map<X500Name, X509Certificate> trustedCertMap = new HashMap<>();

	/**
	 * Accepts any key store with trusted certificates.
	 *
	 * @param trustStore any key store containing trusted certificates (the so-called certificate entries)
	 */
	public SimpleIssuerTrustStore(KeyStore trustStore) {
		// retrieve all trusted certificates from the store to be able to reference them
		// quickly without having to iterate over them
		try {
			Map<X500Name, X509Certificate> trustedCertMap = new HashMap<>();
			Enumeration<String> aliasEnum = trustStore.aliases();
			while (aliasEnum.hasMoreElements()) {
				String alias = aliasEnum.nextElement();
				if (trustStore.isCertificateEntry(alias)) {
					Certificate cert = trustStore.getCertificate(alias);
					// skip weird certificates, this is PKIX only
					if (!(cert instanceof X509Certificate)) {
						continue;
					}
					X509Certificate trustedCert = (X509Certificate) cert;

					// TODO possibly more tests, e.g. w.r.t. validity period in advance?

					trustedCertMap.put(new X500Name(trustedCert.getSubjectX500Principal().getName()), trustedCert);
				}
			}
			this.trustedCertMap = trustedCertMap;
		} catch (Exception e) {
			throw new RuntimeException("Error retrieving trusted certificates from trust store", e);
		}
	}

	public SimpleIssuerTrustStore(List<X509Certificate> trustedCertificates) {
		for (X509Certificate trustedCert : trustedCertificates) {
			trustedCertMap.put(new X500Name(trustedCert.getSubjectX500Principal().getName()), trustedCert);
		}
	}

	private static boolean validateKeyUsageOfCA(boolean[] keyUsage) {
//		if (!keyUsage[KEY_CERT_SIGN]) {
//			throw new CertificateException("CA certificate doesn't have the key usage to sign certificates");
//		}
		return !keyUsage[KEY_CERT_SIGN];
	}

	@Override
	public List<X509Certificate> createCertificationTrustPath(List<X509Certificate> chain) {
		List<X509Certificate> certificationTrustPath = new LinkedList<>();
		// iterate backwards over list to find certificate in trust store
		Iterator<X509Certificate> certIterator = chain.listIterator();
		X509Certificate trustedCert = null;
		while (certIterator.hasNext()) {
			X509Certificate currentCert = certIterator.next();
			certificationTrustPath.add(currentCert);

			X500Name x500Name = new X500Name(currentCert.getIssuerX500Principal().getName());
			trustedCert = trustedCertMap.get(x500Name);
			if (trustedCert != null) {
				certificationTrustPath.add(trustedCert);
				break;
			}
		}

		if (trustedCert != null) {
			return certificationTrustPath;
		}

		return null;
	}

	/**
	 * Validates the certificate chain for an mdoc Document Signer certificate (for
	 * the document types of mEKB or micov, for instance).
	 * <p>
	 * Performs the following checks:
	 * <ul>
	 * <li>Validates that the DS is configured for signature generation.</li>
	 * <li>Validates that the CA certificates are configured for signing
	 * certificates</li>
	 * <li>Validates that the validity period of the certificates includes the
	 * current date.</li>
	 * <li>Verifies that the certificates in the certificate chain have been signed
	 * by the CA certificates that are next in the chain.</li>
	 * <li>Looks for a trusted certificate that has a subject that matches the
	 * issuer field within one of the certificates in the chain - usually the last
	 * certificate.</li>
	 * <li>Verifies that that particular certificate has been signed by the trusted
	 * certificate</li>
	 * </ul>
	 * <p>
	 * The following possible checks are currently not checked:
	 * <ul>
	 * <li>Path-length checks of CA certificates are not carried out</li>
	 * <li>No check is performed on extended key usage / OID</li>
	 * </ul>
	 *
	 * @see IssuerTrustStore#validateCertificationTrustPath(List)
	 */
	@Override
	public boolean validateCertificationTrustPath(List<X509Certificate> certificationTrustPath) {
		if (certificationTrustPath == null || certificationTrustPath.isEmpty()) {
//			throw new IllegalArgumentException(
//					"Certificate chain of document signer is empty (no certificates in signature)");
			return false;
		}

		Iterator<X509Certificate> certIterator = (Iterator<X509Certificate>) certificationTrustPath.iterator();

		X509Certificate leafCert = certIterator.next();

		if (leafCert.getKeyUsage()[DIGITAL_SIGNATURE] == false) {
//			throw new CertificateException("Document Signer certificate is not a signing certificate");
			return false;
		}

		// check if the certificate is currently valid
		// NOTE does not check if it is valid within the validity period of the issuing
		// CA
		try {
			// NOTE throws multiple exceptions derived from CertificateException
			leafCert.checkValidity();
		} catch (CertificateException e) {
			return false;
		}

		// Note that the signature of the trusted certificate itself is not verified even if it is self signed
		X509Certificate prevCert = leafCert;
		X509Certificate caCert;
		while (certIterator.hasNext()) {
			caCert = certIterator.next();

			validateKeyUsageOfCA(caCert.getKeyUsage());
			X500Name nameCert = new X500Name(prevCert.getIssuerX500Principal().getName());
			X500Name nameCA = new X500Name(caCert.getSubjectX500Principal().getName());

			if (!nameCert.equals(nameCA)) {
//				throw new CertificateException(
//						"CA certificate in chain at " + index + " isn't the issuer of the certificate before it");
				return false;
			}

			try {
                try {
                    prevCert.verify(caCert.getPublicKey());
                } catch (InvalidKeyException e) {
                    // Try to decode certificate using BouncyCastleProvider
                    CertificateFactory factory = CertificateFactory.getInstance("X509", new BouncyCastleProvider());
                    X509Certificate prevCertBC = (X509Certificate) factory.generateCertificate(
                            new ByteArrayInputStream(prevCert.getEncoded()));

                    X509Certificate caCertBC = (X509Certificate) factory.generateCertificate(
                            new ByteArrayInputStream(caCert.getEncoded()));

                    prevCertBC.verify(caCertBC.getPublicKey());
                }
			} catch (CertificateException | InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException | SignatureException e) {
//				throw new CertificateException("Certificate chain verification failed at certificate index " + index,
//						e);
				return false;
			}

			prevCert = caCert;
		}

		return true;
	}
}
