package org.multipaz.records.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.records.data.Identity
import org.multipaz.records.data.RecordType
import org.multipaz.records.data.recordTypes
import org.multipaz.records.data.tokenToId

/**
 * Handles `schema` GET request.
 *
 * Returns field names, descriptions, and types for core data and records that are supported by
 * this system-of-record server.
 *
 * Response:
 * ```
 * [
 *   {
 *     "identifier": <record type name>,  // or "core" for core data
 *     "display_name": <record type description>,
 *     "type": {  // "type" also can be one of the primitive types
 *       "type": "complex",
 *       "attributes": [
 *         {
 *           "identifier": <field name>,
 *           "display_name": <field description>,
 *           "type": "string"  // can also be "date", "number", "boolean", "blob", or "picture"
 *                  // also "type" can be an object describing a "complex" type using "attributes",
 *                  // or an "options" type, using "options" map (option ids -> labels)
 *         },
 *         ...
 *       ]
 *     }
 *   },
 *   ...
 * ]
 * ```
 */
suspend fun identitySchema(call: ApplicationCall) {
    call.respondText(
        contentType = ContentType.Application.Json,
        text = buildJsonArray {
            for (recordType in recordTypes.values) {
                add(recordType.toJson())
            }
        }.toString()
    )
}

private fun RecordType.toJson(): JsonObject {
    return buildJsonObject {
        put("identifier", attribute.identifier)
        put("display_name", attribute.displayName)
        when (val type = attribute.type) {
            DocumentAttributeType.String -> put("type", "string")
            DocumentAttributeType.Date -> put("type", "date")
            DocumentAttributeType.Number -> put("type", "number")
            DocumentAttributeType.Boolean -> put("type", "boolean")
            DocumentAttributeType.Blob -> put("type", "blob")
            DocumentAttributeType.Picture -> put("type", "picture")
            is DocumentAttributeType.StringOptions -> putJsonObject("type") {
                put("type", "options")
                putJsonObject("options") {
                    for (option in type.options) {
                        put(option.value ?: option.displayName, option.displayName)
                    }
                }
            }
            is DocumentAttributeType.ComplexType -> putJsonObject("type") {
                put("type", "complex")
                putJsonArray("attributes") {
                    for (recordType in subAttributes.values) {
                        add(recordType.toJson())
                    }
                }
            }
            else -> throw IllegalArgumentException("Unsupported type: $type")
        }
    }
}