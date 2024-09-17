package com.android.identity.server

import com.android.identity.flow.server.Configuration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Configuration common for all servlets packaged in a given the WAR file.
 *
 * Reads properties from common_configuration.json file that must be present in WAR resources.
 */
object CommonConfiguration : Configuration {
    private val values: Map<String, String>

    init {
        val jsonText = ServerResources.getStringResource("common_configuration.json")
            ?: throw IllegalStateException("Resource file common_configuration.json must be present")
        val json = Json.parseToJsonElement(jsonText) as JsonObject
        val map = mutableMapOf<String, String>()
        for (entry in json) {
            val value = entry.value
            map[entry.key] = if (value is JsonPrimitive) {
                value.content
            } else {
                value.toString()
            }
        }
        values = map.toMap()
    }

    override fun getValue(key: String): String? = values[key]
}