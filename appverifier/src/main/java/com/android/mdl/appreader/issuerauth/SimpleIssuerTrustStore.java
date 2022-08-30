package com.android.mdl.appreader.issuerauth;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

/**
 * Trust manager that verifies certificate chains starting with a DS certificate, possibly followed by CA certificates.
 * This trust manager is non-specific to mDL in the sense that it doesn't validate the country and optional state according to the rules
 * set in ISO/IEC 18013-5:2021.
 */
public class SimpleIssuerTrustStore implements IssuerTrustStore {

	private static final int DIGITAL_SIGNATURE = 0;
	private static final int KEY_CERT_SIGN = 5;
	private final KeyStore trustStore;

	// private Map<X500Name, X509Certificate> trustedCertMap = new HashMap<>();

	/**
	 * Accepts any key store with trusted certificates.
	 * <p>
	 * The given trust store is not altered; any change to the key store is reflected; no defensive copy is created.
	 * </p>
	 *
	 * @param trustStore any key store containing trusted certificates (the so-called certificate entries)
	 */
	public SimpleIssuerTrustStore(KeyStore trustStore) {
		if (trustStore == null) {
			throw new IllegalArgumentException("No trust store indicated");
		}
		this.trustStore = trustStore;
	}

	/**
	 * Accepts any key store with trusted certificates.
	 *
	 * @param trustedCertificates a list of trusted certificates
	 */
	public SimpleIssuerTrustStore(List<X509Certificate> trustedCertificates) {
		if (trustedCertificates == null) {
			throw new IllegalArgumentException("No trust store indicated");
		}

		KeyStore truststore;
		try {
			truststore = KeyStore.getInstance(KeyStore.getDefaultType());
			truststore.load(null, null);
			int count = 0;
			for (X509Certificate trustedCertificate : trustedCertificates) {
				truststore.setCertificateEntry(Integer.toString(count++), trustedCertificate);
			}
		} catch (Exception e) {
			throw new RuntimeException("Could not load certificates in default key/trust store");
		}
		this.trustStore = truststore;
	}

	@Override
	public List<X509Certificate> createCertificationTrustPath(List<X509Certificate> chain) throws CertPathBuilderException {
		CertPathBuilder cpb;
		try {
			cpb = CertPathBuilder.getInstance("PKIX");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("PKIX is a required algorithm for Java implementations of CertPathBuilder", e);
		}
		X509CertSelector cSelector = new X509CertSelector();
		cSelector.setCertificate(chain.get(0));

		PKIXBuilderParameters cpbParams;
		try {
			cpbParams = new PKIXBuilderParameters(trustStore, cSelector);
		} catch (KeyStoreException e) {
			throw new RuntimeException("Key store should be initialized and contain a trusted entry", e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new RuntimeException("Key store doesn't contain a trusted entry", e);
		}

		cpbParams.setRevocationEnabled(false);

		CertStoreParameters intermediates = new CollectionCertStoreParameters(chain);
		try {
			cpbParams.addCertStore(CertStore.getInstance("Collection", intermediates));
		} catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
			throw new RuntimeException("Certificate contains an unsupported algorithm", e);
		}

		PKIXCertPathBuilderResult cpbResult;
		try {
			cpbResult = (PKIXCertPathBuilderResult) cpb.build(cpbParams);
		} catch (InvalidAlgorithmParameterException e) {
			throw new RuntimeException("Uncaught exception, blame developer", e);
		}

		CertPath cp = cpbResult.getCertPath();
		List<X509Certificate> certificates = new LinkedList<>();
		for (Certificate certificate : cp.getCertificates()) {
			certificates.add((X509Certificate) certificate);
		}
		certificates.add(cpbResult.getTrustAnchor().getTrustedCert());
		return certificates;
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
	 * @see IssuerTrustStore#validateCertificationTrustPath(List, List)
	 * @return PKIXCertPathValidatorResult the validator result including trust anchor
	 */
	@Override
	public PKIXCertPathValidatorResult validateCertificationTrustPath(
			List<X509Certificate> certChain, List<PKIXCertPathChecker> mdocAndCRLPathCheckers)
			throws KeyStoreException, CertPathValidatorException,
			InvalidAlgorithmParameterException, CertificateException {
		if (certChain == null || certChain.isEmpty()) {
			throw new IllegalArgumentException(
					"Certificate chain of document signer is empty (no certificates in signature)");
		}

		CertificateFactory x509CertFactory;
		try {
			x509CertFactory = CertificateFactory.getInstance("X509");
		} catch (CertificateException e) {
			throw new IllegalArgumentException("X509 is a required algorithm for Java implementations", e);
		}

		CertPath certPath = x509CertFactory.generateCertPath(certChain);

		CertPathValidator cpv;
		try {
			cpv = CertPathValidator.getInstance("PKIX");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("PKIX is a required algorithm for Java implementations", e);
		}

		PKIXParameters cpvParams;
		try {
			cpvParams = new PKIXParameters(trustStore);
		} catch (InvalidAlgorithmParameterException e) {
			throw new IllegalArgumentException("PKIX is a required algorithm for Java implementations", e);
		}

		cpvParams.setRevocationEnabled(false);

		for (PKIXCertPathChecker checker : mdocAndCRLPathCheckers) {
			cpvParams.addCertPathChecker(checker);
		}

		return (PKIXCertPathValidatorResult) cpv.validate(certPath, cpvParams);
	}
}
