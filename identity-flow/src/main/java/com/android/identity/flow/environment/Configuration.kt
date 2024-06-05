package com.android.identity.flow.environment

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

import com.android.identity.util.Logger
import kotlinx.serialization.json.jsonPrimitive

/**
 * Simple interface to access configuration parameters.
 *
 * Parameters can be organized in tree-like structures with dots separating the names of the
 * nodes in that tree, i.e. "componentClass.componentName.valueName".
 */
interface Configuration {
    companion object {
        private val TAG = "Configuration"
    }

    fun getProperty(name: String): String?

    fun getBool(name: String, defaultValue: Boolean = false): Boolean {
        val value = getProperty(name)
        if (value == null) {
            Logger.d(TAG, "getBool: No value configuration value with key $name, return default value $defaultValue")
            return defaultValue
        }
        if (value == "true") {
            return true
        } else if (value == "false") {
            return false
        }
        Logger.d(TAG, "getBool: Unexpected value '$value' with key $name, return default value $defaultValue")
        return defaultValue
    }

    fun getStringList(key: String): List<String> {
        val value = getProperty(key)
        if (value == null) {
            Logger.d(TAG, "getStringList: No value configuration value with key $key")
            return emptyList()
        }
        return Json.parseToJsonElement(value).jsonArray.map { elem -> elem.jsonPrimitive.content }
    }
}