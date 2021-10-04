package com.android.mdl.appreader.util

import android.util.Log
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPoint
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min


object FormatUtil {
    // Helper function to convert a byteArray to HEX string
    fun encodeToString(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }

        return sb.toString()
    }


    private const val CHUNK_SIZE = 2048

    /* Debug print */
    fun debugPrint(tag: String, message: String) {
        var i = 0
        while (i < message.length) {
            Log.d(tag, message.substring(i, min(message.length, i + CHUNK_SIZE)))
            i += CHUNK_SIZE
        }
    }

    /* Debug print */
    fun debugPrintEncodeToString(tag: String, bytes: ByteArray) {
        debugPrint(tag, encodeToString(bytes))
    }

    private const val COSE_KEY_KTY = 1
    private const val COSE_KEY_TYPE_EC2 = 2
    private const val COSE_KEY_EC2_CRV = -1
    private const val COSE_KEY_EC2_X = -2
    private const val COSE_KEY_EC2_Y = -3
    private const val COSE_KEY_EC2_CRV_P256 = 1

    fun cborBuildCoseKey(key: PublicKey): DataItem {
        val ecKey: ECPublicKey = key as ECPublicKey
        val w: ECPoint = ecKey.w
        // X and Y are always positive so for interop we remove any leading zeroes
        // inserted by the BigInteger encoder.
        val x = stripLeadingZeroes(w.affineX.toByteArray())
        val y = stripLeadingZeroes(w.affineY.toByteArray())
        return CborBuilder()
            .addMap()
            .put(COSE_KEY_KTY.toLong(), COSE_KEY_TYPE_EC2.toLong())
            .put(COSE_KEY_EC2_CRV.toLong(), COSE_KEY_EC2_CRV_P256.toLong())
            .put(COSE_KEY_EC2_X.toLong(), x)
            .put(COSE_KEY_EC2_Y.toLong(), y)
            .end()
            .build()[0]
    }

    fun cborEncode(dataItem: DataItem): ByteArray {
        val baos = ByteArrayOutputStream()
        try {
            CborEncoder(baos).encode(dataItem)
        } catch (e: CborException) {
            // This should never happen and we don't want cborEncode() to throw since that
            // would complicate all callers. Log it instead.
            throw IllegalStateException("Unexpected failure encoding data", e)
        }
        return baos.toByteArray()
    }

    fun cborDecode(encodedBytes: ByteArray): DataItem {
        val dataItems = try {
            CborDecoder(ByteArrayInputStream(encodedBytes)).decode()
        } catch (e: CborException) {
            throw IllegalArgumentException("Error decoding CBOR", e)
        }
        require(dataItems.size == 1) {
            ("Unexpected number of items, expected 1 got "
                    + dataItems.size)
        }
        return dataItems[0]
    }

    private fun stripLeadingZeroes(value: ByteArray): ByteArray {
        var n = 0
        while (n < value.size && value[n] == 0x00.toByte()) {
            n++
        }
        return value.copyOfRange(n, value.size)
    }

    private fun cborMapExtractArray(map: DataItem, key: String): List<DataItem> {
        require(map is Map) { "Expected map" }
        val item: DataItem? = map.get(UnicodeString(key))
        require(!(item == null || item !is Array)) { "Expected Array" }
        return item.dataItems
    }

    private fun cborMapExtractArray(map: DataItem?, key: Long): List<DataItem> {
        require(map is Map) { "Expected map" }
        val keyDataItem: DataItem = if (key >= 0) UnsignedInteger(key) else NegativeInteger(key)
        val item = map[keyDataItem]
        require(!(item == null || item !is Array)) { "Expected Array" }
        return item.dataItems
    }


    fun extractDeviceRetrievalMethods(encodedDeviceEngagement: ByteArray): Collection<ByteArray> {
        val ret: MutableCollection<ByteArray> = ArrayList()
        val deviceEngagement = cborDecode(encodedDeviceEngagement)
        val methods = cborMapExtractArray(deviceEngagement, 2)
        for (method in methods) {
            ret.add(cborEncode(method))
        }
        return ret
    }

    fun cborPrettyPrint(dataItem: DataItem): String {
        val sb = java.lang.StringBuilder()
        cborPrettyPrintDataItem(sb, 0, dataItem)
        return sb.toString()
    }

    fun cborPrettyPrint(encodedBytes: ByteArray): String {
        val newLine = "<br>"
        val sb = java.lang.StringBuilder()
        val bais = ByteArrayInputStream(encodedBytes)
        val dataItems = try {
            CborDecoder(bais).decode()
        } catch (e: CborException) {
            throw java.lang.IllegalStateException(e)
        }
        for ((count, dataItem) in dataItems.withIndex()) {
            if (count > 0) {
                sb.append(",$newLine")
            }
            cborPrettyPrintDataItem(sb, 0, dataItem)
        }
        return sb.toString()
    }

    private fun cborPrettyPrintDataItem(
        sb: java.lang.StringBuilder, indent: Int,
        dataItem: DataItem
    ) {
        val space = "&nbsp;"
        val newLine = "<br>"
        val indentBuilder = java.lang.StringBuilder()
        for (n in 0 until indent) {
            indentBuilder.append(space)
        }
        val indentString = indentBuilder.toString()
        if (dataItem.hasTag()) {
            sb.append(String.format("tag %d ", dataItem.tag.value))
        }
        when (dataItem.majorType) {
            MajorType.INVALID ->                 // TODO: throw
                sb.append("**invalid**")
            MajorType.UNSIGNED_INTEGER -> {
                // Major type 0: an unsigned integer.
                val value: BigInteger = (dataItem as UnsignedInteger).value
                sb.append(value)
            }
            MajorType.NEGATIVE_INTEGER -> {
                // Major type 1: a negative integer.
                val value: BigInteger = (dataItem as NegativeInteger).value
                sb.append(value)
            }
            MajorType.BYTE_STRING -> {
                // Major type 2: a byte string.
                val value = (dataItem as ByteString).bytes
                sb.append("[")
                for ((count, b) in value.withIndex()) {
                    if (count > 0) {
                        sb.append(", ")
                    }
                    sb.append(String.format("0x%02x", b))
                }
                sb.append("]")
            }
            MajorType.UNICODE_STRING -> {
                // Major type 3: string of Unicode characters that is encoded as UTF-8 [RFC3629].
                val value = (dataItem as UnicodeString).string
                // TODO: escape ' in |value|
                sb.append("'$value'")
            }
            MajorType.ARRAY -> {

                // Major type 4: an array of data items.
                val items = (dataItem as Array).dataItems
                if (items.size == 0) {
                    sb.append("[]")
                } else if (cborAreAllDataItemsNonCompound(items)) {
                    // The case where everything fits on one line.
                    sb.append("[")
                    for ((count, item) in items.withIndex()) {
                        cborPrettyPrintDataItem(sb, indent, item)
                        if (count + 1 < items.size) {
                            sb.append(", ")
                        }
                    }
                    sb.append("]")
                } else {
                    sb.append("[$newLine$indentString")
                    for ((count, item) in items.withIndex()) {
                        sb.append("$space$space")
                        cborPrettyPrintDataItem(sb, indent + 2, item)
                        if (count + 1 < items.size) {
                            sb.append(",")
                        }
                        sb.append("$newLine $indentString")
                    }
                    sb.append("]")
                }
            }
            MajorType.MAP -> {
                // Major type 5: a map of pairs of data items.
                val keys = (dataItem as Map).keys
                if (keys.isEmpty()) {
                    sb.append("{}")
                } else {
                    sb.append("{$newLine$indentString")
                    for ((count, key) in keys.withIndex()) {
                        sb.append("$space$space")
                        val value = dataItem[key]
                        cborPrettyPrintDataItem(sb, indent + 2, key)
                        sb.append(" : ")
                        cborPrettyPrintDataItem(sb, indent + 2, value)
                        if (count + 1 < keys.size) {
                            sb.append(",")
                        }
                        sb.append("$newLine $indentString")
                    }
                    sb.append("}")
                }
            }
            MajorType.TAG -> throw java.lang.IllegalStateException("Semantic tag data item not expected")
            MajorType.SPECIAL ->                 // Major type 7: floating point numbers and simple data types that need no
                // content, as well as the "break" stop code.
                if (dataItem is SimpleValue) {
                    when (dataItem.simpleValueType) {
                        SimpleValueType.FALSE -> sb.append("false")
                        SimpleValueType.TRUE -> sb.append("true")
                        SimpleValueType.NULL -> sb.append("null")
                        SimpleValueType.UNDEFINED -> sb.append("undefined")
                        SimpleValueType.RESERVED -> sb.append("reserved")
                        SimpleValueType.UNALLOCATED -> sb.append("unallocated")
                    }
                } else if (dataItem is DoublePrecisionFloat) {
                    val df = DecimalFormat(
                        "0",
                        DecimalFormatSymbols.getInstance(Locale.ENGLISH)
                    )
                    df.maximumFractionDigits = 340
                    sb.append(df.format(dataItem.value))
                } else if (dataItem is AbstractFloat) {
                    val df = DecimalFormat(
                        "0",
                        DecimalFormatSymbols.getInstance(Locale.ENGLISH)
                    )
                    df.maximumFractionDigits = 340
                    sb.append(df.format(dataItem.value))
                } else {
                    sb.append("break")
                }
        }
    }

    // Returns true iff all elements in |items| are not compound (e.g. an array or a map).
    private fun cborAreAllDataItemsNonCompound(items: List<DataItem>): Boolean {
        for (item in items) {
            when (item.majorType) {
                MajorType.ARRAY, MajorType.MAP -> return false
                else -> {
                }
            }
        }
        return true
    }
}