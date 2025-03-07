package org.multipaz.sdjwt.util

import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
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
import kotlin.jvm.JvmStatic

/**
 * Superclass for JSON objects that can be serialized into (and deserialized from) base64 strings,
 * such as headers or payloads in JWTs.
 */
abstract class JwtJsonObject {
    protected abstract fun buildJson(): JsonObjectBuilder.() -> Unit

    private fun toJsonObject() = buildJsonObject(buildJson())

    override fun toString() = toJsonObject().toString().encodeToByteArray().toBase64Url()

    protected companion object {
        @JvmStatic
        protected fun parse(input: String): JsonObject {
            return Json.decodeFromString(
                JsonObject.serializer(),
                input.fromBase64Url().decodeToString())
        }
    }
}

internal fun JsonObject.getJsonElement(key: String): JsonElement {
    return this[key] ?: throw IllegalStateException("key $key missing from JSON")
}

internal fun JsonObject.getJsonPrimitive(key: String): JsonPrimitive {
    return getJsonElement(key).jsonPrimitive
}

internal fun JsonObject.getJsonPrimitiveOrNull(key: String): JsonPrimitive? {
    return this[key]?.jsonPrimitive
}

internal fun JsonObject.getString(key: String): String {
    return getJsonPrimitive(key).content
}

internal fun JsonObject.getStringOrNull(key: String): String? {
    return getJsonPrimitiveOrNull(key)?.content
}

internal fun JsonObject.getLong(key: String): Long {
    return getJsonPrimitive(key).long
}

internal fun JsonObject.getLongOrNull(key: String): Long? {
    return getJsonPrimitiveOrNull(key)?.long
}

internal fun JsonObject.getJsonObjectOrNull(key: String): JsonObject? {
    return this[key]?.jsonObject
}

internal fun JsonObject.getJsonArray(key: String): JsonArray {
    return getJsonElement(key).jsonArray
}
