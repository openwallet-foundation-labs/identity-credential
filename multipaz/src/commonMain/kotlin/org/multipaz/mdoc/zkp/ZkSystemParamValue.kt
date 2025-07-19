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
 * An interface for ZK system parameter values.
 */
sealed interface ZkSystemParamValue {
    /**
     * A string value.
     *
     * @property value the value.
     */
    data class StringValue(val value: String): ZkSystemParamValue

    /**
     * A number value.
     *
     * @property value the value.
     */
    data class LongValue(val value: Long): ZkSystemParamValue

    /**
     * A floating point value.
     *
     * @property value the value.
     */
    data class DoubleValue(val value: Double): ZkSystemParamValue

    /**
     * A boolean value.
     *
     * @property value the value.
     */
    data class BooleanValue(val value: Boolean): ZkSystemParamValue

    /**
     * Encodes the value as Cbor.
     *
     * @return a [DataItem].
     */
    fun toDataItem(): DataItem = when (this) {
        is StringValue -> this.value.toDataItem()
        is DoubleValue -> this.value.toDataItem()
        is BooleanValue -> this.value.toDataItem()
        is LongValue -> this.value.toDataItem()
    }

    /**
     * Encodes the value as Json.
     *
     * @return a [JsonElement].
     */
    fun toJson(): JsonElement = when (this) {
        is BooleanValue -> JsonPrimitive(value)
        is DoubleValue -> JsonPrimitive(value)
        is LongValue -> JsonPrimitive(value)
        is StringValue -> JsonPrimitive(value)
    }

    companion object {
        /**
         * Decodes a Cbor value.
         *
         * @param value the [DataItem] to decode.
         * @return a [ZkSystemParamValue].
         */
        fun fromDataItem(value: DataItem): ZkSystemParamValue {
            return when (value) {
                is Tstr -> StringValue(value.value)
                is CborInt -> LongValue(value.asNumber)
                is CborDouble -> DoubleValue(value.value)
                is Simple -> {
                    when (value.value) {
                        Simple.FALSE.value -> BooleanValue(false)
                        Simple.TRUE.value -> BooleanValue(true)
                        else -> throw IllegalArgumentException("Unexpected CBOR")
                    }
                }
                else -> throw IllegalArgumentException("Unexpected Cbor data item $value")
            }
        }

        /**
         * Decodes a Json value.
         *
         * @param value the [JsonElement] to decode.
         * @return a [ZkSystemParamValue].
         */
        fun fromJson(value: JsonElement): ZkSystemParamValue {
            return if (value.jsonPrimitive.isString) {
                StringValue(value.jsonPrimitive.content)
            } else if (value.jsonPrimitive.booleanOrNull != null) {
                BooleanValue(value.jsonPrimitive.booleanOrNull!!)
            } else if (value.jsonPrimitive.longOrNull != null) {
                LongValue(value.jsonPrimitive.longOrNull!!)
            } else if (value.jsonPrimitive.doubleOrNull != null) {
                DoubleValue(value.jsonPrimitive.doubleOrNull!!)
            } else {
                throw IllegalArgumentException("Unexpected JsonElement $value")
            }
        }
    }
}
