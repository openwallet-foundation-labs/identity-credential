package org.multipaz.sdjwt.vc

import kotlinx.serialization.json.JsonArray
import org.multipaz.crypto.Algorithm
import org.multipaz.sdjwt.util.JwtJsonObject
import org.multipaz.sdjwt.util.getString
import org.multipaz.sdjwt.util.getStringOrNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.sdjwt.util.getJsonArray
import org.multipaz.sdjwt.util.getJsonArrayOrNull
import org.multipaz.util.fromBase64Url
import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Header for an (issuer-signed) JWT that is to be used in a SD-JWT VC.
 * It specifies the type 'vc+sd-jwt' and the algorithm used by the
 * issuer to sign the JWT payload (consisting of the base64-url-encoded
 * header, as defined here, a period, and the base64-url-encoded body, as
 * defined in [JwtBody]).
 */
class JwtHeader(val algorithm: Algorithm, val kid: String?, val x5c: X509CertChain?): JwtJsonObject() {
    override fun buildJson(): JsonObjectBuilder.() -> Unit = {
        put("typ", JsonPrimitive(SD_JWT_VC_TYPE))
        put("alg", JsonPrimitive(algorithm.joseAlgorithmIdentifier!!))
        kid?.let {
            put("kid", JsonPrimitive(it))
        }
        x5c?.let {
            put("x5c", buildX5c(it))
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun buildX5c(certChain: X509CertChain): JsonArray {
        val encodedCerts = certChain.certificates.map { cert ->
            JsonPrimitive(
                Base64.encode(cert.encodedCertificate)
            )
        }
        return JsonArray(encodedCerts)
    }

    companion object {

        const val SD_JWT_VC_TYPE = "vc+sd-jwt"

        fun fromString(input: String): JwtHeader {
            val jsonObj = parse(input)
            val typ = jsonObj.getString("typ")
            if (typ != SD_JWT_VC_TYPE) {
                throw IllegalStateException("typ field had illegal value $typ")
            }
            val algorithm = jsonObj.getString("alg")
            val kid = jsonObj.getStringOrNull("kid")
            val x5c = jsonObj.getJsonArrayOrNull("x5c")?.toCertChain()

            return JwtHeader(Algorithm.fromJoseAlgorithmIdentifier(algorithm), kid, x5c)
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun JsonArray.toCertChain(): X509CertChain {
            return X509CertChain(this.map { element ->
                X509Cert(Base64.decode(element.jsonPrimitive.content))
            })
        }
    }
}