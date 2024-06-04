package com.android.identity.wallet.server
import kotlinx.serialization.json.Json

import com.android.identity.flow.environment.Configuration
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

class ServerConfiguration(jsonConfig: String) : Configuration {
    private val configFile = File(jsonConfig)
    private val config = if (configFile.canRead()) {
        println("Loaded config file: ${configFile.absolutePath}")
        Json.parseToJsonElement(configFile.bufferedReader().readText()).jsonObject
    } else {
        println("Missing config file: ${configFile.absolutePath}")
        null
    }

    override fun getProperty(name: String): String? {
        var value: JsonElement? = config
        for (key in name.split(".")) {
            if (value !is JsonObject) {
                return null
            }
            value = value[key]
        }
        return when (value) {
            null, is JsonNull -> null
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
    }

}