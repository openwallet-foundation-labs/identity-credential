package com.android.identity.sdjwt.vc

import com.android.identity.crypto.Algorithm
import com.android.identity.sdjwt.util.JwtJsonObject
import com.android.identity.sdjwt.util.getString
import com.android.identity.sdjwt.util.getStringOrNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Header for an (issuer-signed) JWT that is to be used in a SD-JWT VC.
 * It specifies the type 'vc+sd-jwt' and the algorithm used by the
 * issuer to sign the JWT payload (consisting of the base64-url-encoded
 * header, as defined here, a period, and the base64-url-encoded body, as
 * defined in [JwtBody]).
 */
class JwtHeader(val algorithm: Algorithm, val kid: String?): JwtJsonObject() {
    override fun buildJson(): JsonObjectBuilder.() -> Unit = {
        put("typ", JsonPrimitive(SD_JWT_VC_TYPE))
        put("alg", JsonPrimitive(algorithm.jwseAlgorithmIdentifier))
        kid?.let {
            put("kid", JsonPrimitive(it))
        }
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
            return JwtHeader(Algorithm.fromJwseAlgorithmIdentifier(algorithm), kid)
        }
    }
}