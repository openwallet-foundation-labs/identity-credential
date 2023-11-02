package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.MajorType
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

        // TODO check if decoded is correct or if we need the internal representation
        val verificationResult = com.android.identity.internal.Util.coseSign1CheckSignature(decoded, byteArrayOf(), vicalSigningCert!!.publicKey)
        if (!verificationResult) {
            return VicalVerificationResult(VicalVerificationResult.Code.VERIFICATION_FAILED, vicalSigningCert, null)
        }
        val payload = retrievePayload(decoded)
        return VicalVerificationResult(VicalVerificationResult.Code.VERIFICATION_SUCCEEDED, vicalSigningCert, payload)
    }


    fun retrievePayload(
        coseSign1: DataItem
    ): ByteString {
        // TODO should not all be requires...
        require(coseSign1.majorType == MajorType.ARRAY) { "Data item is not an array" }
        val items = (coseSign1 as Array).dataItems
        require(items.size >= 4) { "Expected at least four items in COSE_Sign1 array" }
        val payload = items[2]
        require(payload.majorType == MajorType.BYTE_STRING) { "Payload should be of type BYTE_STRING" }
        return payload as ByteString
    }

    companion object {
        private val NO_AAD = ByteArray(0)
    }
}