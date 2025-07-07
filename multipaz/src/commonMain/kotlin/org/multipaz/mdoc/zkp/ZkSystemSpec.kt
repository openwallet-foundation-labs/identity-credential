package org.multipaz.mdoc.zkp

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.CborDouble
import org.multipaz.cbor.CborFloat
import org.multipaz.cbor.CborInt
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborMap
import org.multipaz.cbor.toDataItem

sealed interface ParamValue {
    data class StringValue(val value: String) : ParamValue
    data class ByteArrayValue(val value: ByteArray) : ParamValue
    data class FloatValue(val value: Float) : ParamValue
    data class LongValue(val value: Long) : ParamValue
    data class DoubleValue(val value: Double) : ParamValue
    data class BooleanValue(val value: Boolean) : ParamValue

    fun toDataItem(): DataItem  = when (this) {
        is StringValue -> this.value.toDataItem()
        is ByteArrayValue -> this.value.toDataItem()
        is DoubleValue -> this.value.toDataItem()
        is BooleanValue -> this.value.toDataItem()
        is FloatValue -> this.value.toDataItem()
        is LongValue -> this.value.toDataItem()
    }
}
/**
 * ZkSystemSpec represents the specifications of a ZK System.
 *
 * @property id the unique identifier of the ZK system.
 * @property system the name of the ZK system.
 * @property params parameters for the ZK system.
 */
data class ZkSystemSpec (
    val id: String,
    val system: String,
    private val _params: MutableMap<String, ParamValue> = mutableMapOf()
) {
    val params: Map<String, ParamValue>
        get() = _params

    fun toDataItem(): DataItem {
        return buildCborMap {
            put("id", this@ZkSystemSpec.id)
            put("system", this@ZkSystemSpec.system)
            if (params.isNotEmpty()) {
                putCborMap("params") {
                    params.forEach { (key, value) ->
                        put(key, value.toDataItem())
                    }
                }
            }
        }
    }

    inline fun <reified T> getParam(key: String): T? {
        return when (val paramValue = params[key]) {
            is ParamValue.StringValue -> paramValue.value as? T
            is ParamValue.ByteArrayValue -> paramValue.value as? T
            is ParamValue.FloatValue -> paramValue.value as? T
            is ParamValue.LongValue -> paramValue.value as? T
            is ParamValue.DoubleValue -> paramValue.value as? T
            is ParamValue.BooleanValue -> paramValue.value as? T

            null -> null
        }
    }

    /**
     * Adds a String parameter to the ZK system specification.
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: String) {
        _params[key] = ParamValue.StringValue(value)
    }

    /**
     * Adds a ByteArray parameter to the ZK system specification.
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: ByteArray) {
        _params[key] = ParamValue.ByteArrayValue(value)
    }

    /**
     * Adds a Float parameter to the ZK system specification.
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: Float) {
        _params[key] = ParamValue.FloatValue(value)
    }

    /**
     * Adds a Long parameter to the ZK system specification.
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: Long) {
        _params[key] = ParamValue.LongValue(value)
    }

    /**
     * Adds a Double parameter to the ZK system specification.
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: Double) {
        _params[key] = ParamValue.DoubleValue(value)
    }

    /**
     * Adds a Boolean parameter to the ZK system specification.
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, value: Boolean) {
        _params[key] = ParamValue.BooleanValue(value)
    }

    /**
     * Adds a DataItem parameter to the ZK system specification. Will add the parameter to the
     * primitive value of the DataItem.
     * @param key the key of the parameter.
     * @param value the value of the parameter.
     */
    fun addParam(key: String, dataItem: DataItem): Boolean {
        when (dataItem) {
            is Tstr -> addParam(key, dataItem.value)
            is Bstr -> addParam(key, dataItem.value)
            is CborFloat -> addParam(key, dataItem.value)
            is CborInt -> addParam(key, dataItem.asNumber)
            is CborDouble -> addParam(key, dataItem.value)

            is Simple -> {
                when (dataItem.value) {
                    Simple.FALSE.value -> addParam(key, false)
                    Simple.TRUE.value -> addParam(key, true)
                    else -> return false
                }
            }

            else -> return false
        }

        return true
    }
}
