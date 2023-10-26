package com.android.mdl.appreader.readercertgen;

import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.Optional;

/**
 * Generates a key pair for a specific curve, creates a reader certificate around it using
 * the given issuing CA certificate and private key to sign it.
 * <p>
 * Usage:
 * final KeyPair readerKeyPair = generateECDSAKeyPair(curve);
 * X509Certificate dsCertificate = createReaderCertificate(readerKeyPair, iacaCertificate, iacaKeyPair.getPrivate());
 */
public final class ReaderCertificateGenerator {

	private ReaderCertificateGenerator() {
		// avoid instantiation
	}

	public static KeyPair generateECDSAKeyPair(String curve) {
		try {
			// NOTE older devices may not have the right BC installed for this to work
			KeyPairGenerator kpg;
			if (curve.equalsIgnoreCase("Ed25519") || curve.equalsIgnoreCase("Ed448")) {
				kpg = KeyPairGenerator.getInstance(curve, new BouncyCastleProvider());
			} else {
				kpg = KeyPairGenerator.getInstance("EC", new BouncyCastleProvider());
				kpg.initialize(new ECGenParameterSpec(curve));
			}
			System.out.println(kpg.getProvider().getInfo());
			return kpg.generateKeyPair();
		} catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
			throw new RuntimeException(e);
		}
	}

	public static X509Certificate createReaderCertificate(KeyPair dsKeyPair, X509Certificate issuerCert,
														  PrivateKey issuerPrivateKey) throws Exception {
		DataMaterial data = new DataMaterial() {

			@Override
			public String subjectDN() {
				return "C=UT, CN=Google mDoc Reader";
			}

			@Override
			public String issuerDN() {
				// must match DN of issuer character-by-character
				// TODO change for other generators
				return issuerCert.getSubjectX500Principal().getName();

				// reorders string, do not use
				// return issuerCert.getSubjectX500Principal().getName();
			}

			@Override
			public Optional<String> issuerAlternativeName() {
				// NOTE always interpreted as URL for now
				return Optional.of("https://www.google.com/");
			}
		};

		CertificateMaterial certData = new CertificateMaterial() {

			@Override
			public BigInteger serialNumber() {
				// TODO change
				return new BigInteger("476f6f676c655f546573745f44535f31", 16);
			}

			@Override
			public Date startDate() {
				return EncodingUtil.parseShortISODate("2023-01-01");
			}

			@Override
			public Date endDate() {
				return EncodingUtil.parseShortISODate("2024-01-01");
			}

			@Override
			public int pathLengthConstraint() {
				return CertificateMaterial.PATHLENGTH_NOT_A_CA;
			}

			@Override
			public int keyUsage() {
				return KeyUsage.digitalSignature;
			}

			@Override
			public Optional<String> extendedKeyUsage() {
				// TODO change for reader cert
				return Optional.of("1.0.18013.5.1.6");
			}
		};

		KeyMaterial keyData = new KeyMaterial() {

			@Override
			public PublicKey publicKey() {
				return dsKeyPair.getPublic();
			}

			@Override
			public String signingAlgorithm() {
				return "SHA384WithECDSA";
			}

			@Override
			public PrivateKey signingKey() {
				return issuerPrivateKey;
			}

			@Override
			public Optional<X509Certificate> issuerCertificate() {
				return Optional.of(issuerCert);
			}
		};

		// C.1.7.2

		X509Certificate readerCert = CertificateGenerator.generateCertificate(data, certData, keyData);

		return readerCert;
	}
}
