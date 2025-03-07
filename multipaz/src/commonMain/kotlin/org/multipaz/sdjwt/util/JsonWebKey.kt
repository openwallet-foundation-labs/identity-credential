package org.multipaz.sdjwt.util

import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.EcPublicKeyOkp
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A JsonWeb(Public)Key, which can be serialized into JSON, or converted into
 * a [EcPublicKey] object. It can be initialized either from a [EcPublicKey], or
 * from a [JsonObject].
 *
 * The JSON object expected during initialization, and returned when converting
 * this JsonWebKey to JSON, has the format:
 *
 * {
 *   jwk: {
 *          kty: ...
 *          crv: ...
 *          ...
 *        }
 * }
 *
 */
class JsonWebKey {

    private val pubKey: EcPublicKey

    constructor(pubKey: EcPublicKey) {
        this.pubKey = pubKey
    }

    constructor(jwk: JsonObject) {
        pubKey = jwk.getObject("jwk").toEcPublicKey
    }

    val asEcPublicKey: EcPublicKey
        get() = pubKey

    val asJwk: JsonObject
        get() = buildJsonObject {
            put("jwk", toRawJwk {})
        }

    fun toRawJwk(block: JsonObjectBuilder.() -> Unit): JsonObject {
        return buildJsonObject {
            when (pubKey) {
                is EcPublicKeyOkp -> {
                    put("kty", JsonPrimitive("OKP"))
                    put("crv", JsonPrimitive(pubKey.curve.jwkName))
                    put("x", JsonPrimitive(pubKey.x.toBase64Url()))
                }
                is EcPublicKeyDoubleCoordinate -> {
                    put("kty", JsonPrimitive("EC"))
                    put("crv", JsonPrimitive(pubKey.curve.jwkName))
                    put("x", JsonPrimitive(pubKey.x.toBase64Url()))
                    put("y", JsonPrimitive(pubKey.y.toBase64Url()))
                }
                else -> throw IllegalStateException("Unsupported key $pubKey")
            }
            block()
        }
    }

    private companion object {

        fun convertJwkToEcPublicKey(key: JsonObject): EcPublicKey {

            return when(val kty = key.getString("kty")) {
                "OKP" -> {
                    EcPublicKeyOkp(
                        EcCurve.fromJwkName(key.getString("crv")),
                        key.getString("x").fromBase64Url()
                    )
                }
                "EC" -> {
                    EcPublicKeyDoubleCoordinate(
                        EcCurve.fromJwkName(key.getString("crv")),
                        key.getString("x").fromBase64Url(),
                        key.getString("y").fromBase64Url()
                    )
                }
                else -> throw IllegalArgumentException("Not supporting key type $kty")
            }
        }

        private fun JsonObject.getString(key: String): String {
            return this[key]?.jsonPrimitive?.content
                ?: throw IllegalStateException("missing or invalid '$key' entry in JWK $this")
        }
    }

    private fun JsonObject.getObject(key: String): JsonObject {
        return this[key]?.jsonObject
            ?: throw IllegalStateException("missing or invalid '$key' entry in JWK $this")
    }

    private val JsonObject.toEcPublicKey: EcPublicKey
        get() = convertJwkToEcPublicKey(this)
}
