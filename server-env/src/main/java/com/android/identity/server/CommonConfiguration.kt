package org.multipaz.server

import org.multipaz.flow.server.Configuration
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
        val map = mutableMapOf<String, String>()
        val jsonText = ServerResources.getStringResource("common_configuration.json")
        if (jsonText != null) {
            val json = Json.parseToJsonElement(jsonText) as JsonObject
            for (entry in json) {
                val value = entry.value
                map[entry.key] = if (value is JsonPrimitive) {
                    value.content
                } else {
                    value.toString()
                }
            }
        }
        values = map.toMap()
    }

    override fun getValue(key: String): String? = values[key]
}