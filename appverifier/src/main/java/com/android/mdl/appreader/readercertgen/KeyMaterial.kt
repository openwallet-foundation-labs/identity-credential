package com.android.mdl.appreader.readercertgen

import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Optional

interface KeyMaterial {
    fun publicKey(): PublicKey
    fun signingAlgorithm(): String
    fun issuerCertificate(): Optional<X509Certificate>
    fun signingKey(): PrivateKey
}