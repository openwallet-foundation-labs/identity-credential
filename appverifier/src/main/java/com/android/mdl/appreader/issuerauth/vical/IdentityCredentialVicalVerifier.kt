package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.model.DataItem
import java.security.cert.X509Certificate

class IdentityCredentialVicalVerifier : VicalVerifier {
    @Throws(Exception::class)
    override fun verifyCose1Signature(signatureWithData: ByteArray): VicalVerificationResult {
        val decoded: DataItem = try {
            IdentityUtil.cborDecode(signatureWithData)
        } catch (e: Exception) {
            return VicalVerificationResult(VicalVerificationResult.Code.DECODING_ERROR, null, null)
        }
        val vicalSigningChain: List<X509Certificate?>? = try {
            IdentityUtil.coseSign1GetX5Chain(decoded)
        } catch (e: Exception) {
            return VicalVerificationResult(VicalVerificationResult.Code.DECODING_ERROR, null, null)
        }

        // TODO maybe allow for more certs
        if (vicalSigningChain?.isEmpty()!!) {
            return VicalVerificationResult(VicalVerificationResult.Code.DECODING_ERROR, null, null)
        }

        // NOTE assumes leaf is first certificate
        val vicalSigningCert = vicalSigningChain[0]
        val verificationResult =
            IdentityUtil.coseSign1CheckSignature(decoded, NO_AAD, vicalSigningCert!!.publicKey)
        val code =
            if (verificationResult) VicalVerificationResult.Code.VERIFICATION_SUCCEEDED else VicalVerificationResult.Code.VERIFICATION_FAILED
        return VicalVerificationResult(code, vicalSigningCert, decoded)
    }

    companion object {
        private val NO_AAD = ByteArray(0)
    }
}