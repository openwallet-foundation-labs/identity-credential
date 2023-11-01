package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.DataItem
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.PrivateKey
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate

/**
 * Can be used to generate a COSE1 signature over data.
 *
 * The certificate and data will be included in the CBOR envelope according to ISO/IEC 18013-5.
 * The protected header in the signing structure will contain the algorithm indication:
 * <pre>
 * [
 * "Signature1",
 * {
 * "type": "Buffer",
 * "data": [
 * 161,
 * 1,
 * 38
 * ]
 * },
 * {
 * "type": "Buffer",
 * "data": []
 * },
 * {
 * "type": "Buffer",
 * "data": [
 * 0
 * ]
 * }
 * ]
</pre> *
 *
 * @author UL TS BV
 */
class IdentityCredentialVicalSigner(
    private val signCert: X509Certificate,
    private val signKey: PrivateKey,
    private val signatureAlgorithm: String
) : VicalSigner {
    @Throws(CertificateEncodingException::class)
    override fun createCose1Signature(vicalData: ByteArray): ByteArray {
        val signedData: DataItem = IdentityUtil.coseSign1Sign(
            signKey, signatureAlgorithm, vicalData, null, listOf(
                signCert
            )
        )
        try {
            ByteArrayOutputStream().use { outStream ->
                val enc = CborEncoder(outStream)
                enc.encode(signedData)
                return outStream.toByteArray()
            }
        } catch (e: IOException) {
            throw RuntimeException("ByteArrayOutputStream should not throw I/O exceptions", e)
        } catch (e: CborException) {
            // TODO provide better runtime exception
            throw RuntimeException("Error encoding newly generated signature", e)
        }
    }
}