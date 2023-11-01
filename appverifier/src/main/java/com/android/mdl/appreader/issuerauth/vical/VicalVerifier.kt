package com.android.mdl.appreader.issuerauth.vical

interface VicalVerifier {
    @Throws(Exception::class)
    fun verifyCose1Signature(signatureWithData: ByteArray): VicalVerificationResult
}