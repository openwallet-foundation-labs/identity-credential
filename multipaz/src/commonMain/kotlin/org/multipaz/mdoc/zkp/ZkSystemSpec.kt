package org.multipaz.mdoc.zkp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.multipaz.cbor.CborDouble
import org.multipaz.cbor.CborInt
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.toDataItem

/**
 * ZkSystemSpec represents the specifications of a ZK System.
 *
 * @property id the unique identifier of the ZK system.
 * @property system the name of the ZK system.
 */
data class ZkSystemSpec (
    val id: String,
    val system: String,
) {
    private val _params: MutableMap<String, ZkSystemParamValue> = mutableMapOf()

    /**
     * Parameters for the ZK system.
     */
    val params: Map<String, ZkSystemParamValue>
        get() = _params

    /**
     * Gets a parameter
     *
     * @param key the key for the parameter.
     * @return the value or `null` if not found or not the requested type.
     */
    inline fun <reified T> getParam(key: String): T? {
        return when (val paramValue = params[key]) {
            is ZkSystemParamValue.StringValue -> paramValue.value as? T
            is ZkSystemParamValue.LongValue -> paramValue.value as? T
            is ZkSystemParamValue.DoubleValue -> paramValue.value as? T
            is ZkSystemParamValue.BooleanValue -> paramValue.value as? T
            null -> null
        }
    }

    /**
     * Adds a String parameter to the ZK system specification.
     *
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: String) {
        _params[key] = ZkSystemParamValue.StringValue(value)
    }

    /**
     * Adds a Long parameter to the ZK system specification.
     *
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: Long) {
        _params[key] = ZkSystemParamValue.LongValue(value)
    }

    /**
     * Adds a Double parameter to the ZK system specification.
     *
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: Double) {
        _params[key] = ZkSystemParamValue.DoubleValue(value)
    }

    /**
     * Adds a Boolean parameter to the ZK system specification.
     *
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: Boolean) {
        _params[key] = ZkSystemParamValue.BooleanValue(value)
    }

    /**
     * Adds parameter to the ZK system specification encoded in CBOR.
     *
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: DataItem) {
        _params[key] = ZkSystemParamValue.fromDataItem(value)
    }

    /**
     * Adds parameter to the ZK system specification encoded in JSON.
     *
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: JsonElement) {
        _params[key] = ZkSystemParamValue.fromJson(value)
    }
}
