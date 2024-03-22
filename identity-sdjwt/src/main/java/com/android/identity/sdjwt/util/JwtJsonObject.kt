package com.android.identity.sdjwt.util

import com.android.identity.util.fromBase64
import com.android.identity.util.toBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Superclass for JSON objects that can be serialized into (and deserialized from) base64 strings,
 * such as headers or payloads in JWTs.
 */
abstract class JwtJsonObject {
    protected abstract fun buildJson(): JsonObjectBuilder.() -> Unit

    private fun toJsonObject() = buildJsonObject(buildJson())

    override fun toString() = toJsonObject().toString().toByteArray().toBase64

    protected companion object {
        @JvmStatic
        protected fun parse(input: String): JsonObject {
            return Json.decodeFromString(
                JsonObject.serializer(),
                String(input.fromBase64))
        }

        private fun getJsonElement(obj: JsonObject, key: String): JsonElement {
            return obj[key] ?: throw IllegalStateException("key $key missing from JSON")
        }

        @JvmStatic
        protected fun getJsonPrimitive(obj: JsonObject, key: String): JsonPrimitive {
            return getJsonElement(obj, key).jsonPrimitive
        }

        protected fun getJsonPrimitiveOrNull(obj: JsonObject, key: String): JsonPrimitive? {
            return obj[key]?.jsonPrimitive
        }

        @JvmStatic
        protected fun getString(obj: JsonObject, key: String): String {
            return getJsonPrimitive(obj, key).content
        }

        @JvmStatic
        protected fun getStringOrNull(obj: JsonObject, key: String): String? {
            return getJsonPrimitiveOrNull(obj, key)?.content
        }

        @JvmStatic
        protected fun getLong(obj: JsonObject, key: String): Long {
            return getJsonPrimitive(obj, key).long
        }

        @JvmStatic
        protected fun getLongOrNull(obj: JsonObject, key: String): Long? {
            return getJsonPrimitiveOrNull(obj, key)?.long
        }

        @JvmStatic
        protected fun getJsonObjectOrNull(obj: JsonObject, key: String): JsonObject? {
            return obj[key]?.jsonObject
        }

        @JvmStatic
        protected fun getJsonArray(obj: JsonObject, key: String): JsonArray {
            return getJsonElement(obj, key).jsonArray
        }
    }
}