package com.android.mdl.appreader.readercertgen

import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Optional

data class KeyMaterial(
    val publicKey: PublicKey,
    val signingAlgorithm: String,
    val issuerCertificate: Optional<X509Certificate>,
    val signingKey: PrivateKey,
)
