package com.android.mdl.appreader.readercertgen;

import java.util.Optional;

public interface DataMaterial {
	String subjectDN();

	String issuerDN();

	Optional<String> issuerAlternativeName();
}

