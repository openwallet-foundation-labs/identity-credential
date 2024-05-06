package com.android.identity.sdjwt.presentation

import com.android.identity.crypto.Algorithm
import com.android.identity.sdjwt.util.JwtJsonObject
import com.android.identity.sdjwt.util.getString
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive

class KeyBindingHeader(val algorithm: Algorithm): JwtJsonObject()  {

    override fun buildJson(): JsonObjectBuilder.() -> Unit = {
        put("typ", JsonPrimitive(KEY_BINDING_JWT_TYPE))
        put("alg", JsonPrimitive(algorithm.jwseAlgorithmIdentifier))
    }

    companion object {

        const val KEY_BINDING_JWT_TYPE = "kb+jwt"

        fun fromString(input: String): KeyBindingHeader {
            val jsonObj = parse(input)
            val typ = jsonObj.getString("typ")
            if (typ != KEY_BINDING_JWT_TYPE) {
                throw IllegalStateException("typ field had illegal value $typ")
            }
            val algorithm = jsonObj.getString("alg")
            return KeyBindingHeader(Algorithm.fromJwseAlgorithmIdentifier(algorithm))
        }
    }
}
