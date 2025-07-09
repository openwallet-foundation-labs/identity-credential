package org.multipaz.records.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.multipaz.records.data.IdentityData
import org.multipaz.records.data.Identity
import org.multipaz.records.data.TokenType
import org.multipaz.records.data.idToToken
import org.multipaz.records.data.recordTypes
import org.multipaz.records.data.toDataItem
import org.multipaz.records.data.tokenToId
import kotlin.time.Duration

/**
 * Handles `create` or `update` POST requests.
 *
 * Creates a new or updates existing [Identity] in the storage.
 *
 * Request format:
 * ```
 * {
 *     "token": "...",  // only when updating
 *     "core": { "field1": "value1", ... }
 *     "records": {
 *        "recordType1": record1,
 *        ...
 *     }
 * }
 * ```
 *
 * When updating, new data is merged with existing one, see [IdentityData.merge] for details.
 *
 * Response:
 * ```
 * {
 *     "token": "...."
 * }
 * ```
 * */
suspend fun identityPut(call: ApplicationCall, create: Boolean) {
    val request = Json.parseToJsonElement(call.receiveText()) as JsonObject
    val coreAttributes = recordTypes["core"]!!.subAttributes
    val common = request["core"]!!.jsonObject.asIterable().associate { (key, value) ->
        Pair(key, value.toDataItem(coreAttributes[key]!!))
    }
    val records = request["records"]!!.jsonObject.asIterable().associate { (recordTypeId, recordMap) ->
        val recordType = recordTypes[recordTypeId]!!
        Pair(recordTypeId, recordMap.jsonObject.asIterable().associate { (recordId, record) ->
            Pair(recordId, record.toDataItem(recordType))
        })
    }
    val token = if (create) {
        val record = Identity.create(IdentityData(common, records))
        idToToken(TokenType.FE_TOKEN, record.id, Duration.INFINITE)
    } else {
        val token = request["token"]!!.jsonPrimitive.content
        val record = Identity.findById(tokenToId(TokenType.FE_TOKEN, token))
        record.data = record.data.merge(common, records)
        record.save()
        token
    }
    call.respondText(
        contentType = ContentType.Application.Json,
        text = buildJsonObject {
            put("token", token)
        }.toString()
    )
}