package com.android.mdl.appreader.certutil;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.ECGenParameterSpec;

public final class ECKeyPairGenerator {

	private ECKeyPairGenerator() {
		// avoid instantiation
	}
	
	public static KeyPair generateKeyPair(String name) {
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
			kpg.initialize(new ECGenParameterSpec(name));
			return kpg.generateKeyPair();
		} catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
			throw new RuntimeException(e);
		}
	}

}
