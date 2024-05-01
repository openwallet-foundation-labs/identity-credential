package com.android.mdl.appreader.readercertgen

import java.util.Optional

data class DataMaterial(
    val subjectDN: String,
    val issuerDN: String,
    val issuerAlternativeName: Optional<String>,
)
