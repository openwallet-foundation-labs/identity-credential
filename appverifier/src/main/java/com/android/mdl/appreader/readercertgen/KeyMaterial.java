package com.android.mdl.appreader.readercertgen;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Optional;

public interface KeyMaterial {
	PublicKey publicKey();

	String signingAlgorithm();

	Optional<X509Certificate> issuerCertificate();

	PrivateKey signingKey();
}

