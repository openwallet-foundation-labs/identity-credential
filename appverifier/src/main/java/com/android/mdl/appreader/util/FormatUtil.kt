package com.android.mdl.appreader.util

import android.icu.text.SimpleDateFormat
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.AbstractFloat
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.DoublePrecisionFloat
import co.nstant.`in`.cbor.model.MajorType
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.NegativeInteger
import co.nstant.`in`.cbor.model.SimpleValue
import co.nstant.`in`.cbor.model.SimpleValueType
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object FormatUtil {

    // Helper function to convert a byteArray to HEX string
    fun encodeToString(bytes: ByteArray): String {
        val stringBuilder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            stringBuilder.append(String.format("%02x", byte))
        }
        return stringBuilder.toString()
    }

    fun cborPrettyPrint(encodedBytes: ByteArray): String {
        val newLine = "<br>"
        val stringBuilder = StringBuilder()
        val inputStream = ByteArrayInputStream(encodedBytes)
        val dataItems = try {
            CborDecoder(inputStream).decode()
        } catch (e: CborException) {
            throw IllegalStateException(e)
        }
        for ((count, dataItem) in dataItems.withIndex()) {
            if (count > 0) {
                stringBuilder.append(",$newLine")
            }
            cborPrettyPrintDataItem(stringBuilder, 0, dataItem)
        }
        return stringBuilder.toString()
    }

    private fun cborPrettyPrintDataItem(
        stringBuilder: StringBuilder,
        indent: Int,
        dataItem: DataItem
    ) {
        val space = "&nbsp;"
        val newLine = "<br>"
        val indentBuilder = StringBuilder()
        for (n in 0 until indent) {
            indentBuilder.append(space)
        }
        val indentString = indentBuilder.toString()
        if (dataItem.hasTag()) {
            stringBuilder.append(String.format("tag %d ", dataItem.tag.value))
        }
        when (dataItem.majorType) {
            MajorType.INVALID ->                 // TODO: throw
                stringBuilder.append("**invalid**")

            MajorType.UNSIGNED_INTEGER -> {
                // Major type 0: an unsigned integer.
                val value: BigInteger = (dataItem as UnsignedInteger).value
                stringBuilder.append(value)
            }

            MajorType.NEGATIVE_INTEGER -> {
                // Major type 1: a negative integer.
                val value: BigInteger = (dataItem as NegativeInteger).value
                stringBuilder.append(value)
            }

            MajorType.BYTE_STRING -> {
                // Major type 2: a byte string.
                val value = (dataItem as ByteString).bytes
                stringBuilder.append("[")
                for ((count, b) in value.withIndex()) {
                    if (count > 0) {
                        stringBuilder.append(", ")
                    }
                    stringBuilder.append(String.format("0x%02x", b))
                }
                stringBuilder.append("]")
            }

            MajorType.UNICODE_STRING -> {
                // Major type 3: string of Unicode characters that is encoded as UTF-8 [RFC3629].
                val value = (dataItem as UnicodeString).string
                // TODO: escape ' in |value|
                stringBuilder.append("'$value'")
            }

            MajorType.ARRAY -> {

                // Major type 4: an array of data items.
                val items = (dataItem as Array).dataItems
                if (items.size == 0) {
                    stringBuilder.append("[]")
                } else if (cborAreAllDataItemsNonCompound(items)) {
                    // The case where everything fits on one line.
                    stringBuilder.append("[")
                    for ((count, item) in items.withIndex()) {
                        cborPrettyPrintDataItem(stringBuilder, indent, item)
                        if (count + 1 < items.size) {
                            stringBuilder.append(", ")
                        }
                    }
                    stringBuilder.append("]")
                } else {
                    stringBuilder.append("[$newLine$indentString")
                    for ((count, item) in items.withIndex()) {
                        stringBuilder.append("$space$space")
                        cborPrettyPrintDataItem(stringBuilder, indent + 2, item)
                        if (count + 1 < items.size) {
                            stringBuilder.append(",")
                        }
                        stringBuilder.append("$newLine $indentString")
                    }
                    stringBuilder.append("]")
                }
            }

            MajorType.MAP -> {
                // Major type 5: a map of pairs of data items.
                val keys = (dataItem as Map).keys
                if (keys.isEmpty()) {
                    stringBuilder.append("{}")
                } else {
                    stringBuilder.append("{$newLine$indentString")
                    for ((count, key) in keys.withIndex()) {
                        stringBuilder.append("$space$space")
                        val value = dataItem[key]
                        cborPrettyPrintDataItem(stringBuilder, indent + 2, key)
                        stringBuilder.append(" : ")
                        cborPrettyPrintDataItem(stringBuilder, indent + 2, value)
                        if (count + 1 < keys.size) {
                            stringBuilder.append(",")
                        }
                        stringBuilder.append("$newLine $indentString")
                    }
                    stringBuilder.append("}")
                }
            }

            MajorType.TAG -> throw IllegalStateException("Semantic tag data item not expected")
            MajorType.SPECIAL ->                 // Major type 7: floating point numbers and simple data types that need no
                // content, as well as the "break" stop code.
                if (dataItem is SimpleValue) {
                    when (dataItem.simpleValueType) {
                        SimpleValueType.FALSE -> stringBuilder.append("false")
                        SimpleValueType.TRUE -> stringBuilder.append("true")
                        SimpleValueType.NULL -> stringBuilder.append("null")
                        SimpleValueType.UNDEFINED -> stringBuilder.append("undefined")
                        SimpleValueType.RESERVED -> stringBuilder.append("reserved")
                        SimpleValueType.UNALLOCATED -> stringBuilder.append("unallocated")
                    }
                } else if (dataItem is DoublePrecisionFloat) {
                    val df = DecimalFormat(
                        "0",
                        DecimalFormatSymbols.getInstance(Locale.ENGLISH)
                    )
                    df.maximumFractionDigits = 340
                    stringBuilder.append(df.format(dataItem.value))
                } else if (dataItem is AbstractFloat) {
                    val df = DecimalFormat(
                        "0",
                        DecimalFormatSymbols.getInstance(Locale.ENGLISH)
                    )
                    df.maximumFractionDigits = 340
                    stringBuilder.append(df.format(dataItem.value))
                } else {
                    stringBuilder.append("break")
                }
        }
    }

    // Returns true iff all elements in |items| are not compound (e.g. an array or a map).
    private fun cborAreAllDataItemsNonCompound(items: List<DataItem>): Boolean {
        for (item in items) {
            when (item.majorType) {
                MajorType.ARRAY, MajorType.MAP -> return false
                else -> {}
            }
        }
        return true
    }

    fun millisecondsToFullDateTimeString(milliseconds: Long): String {
        val simpleDateFormat = SimpleDateFormat("MMM d, yyyy 'at' hh:mm:ss a")
        return simpleDateFormat.format(milliseconds)
    }

    fun millisecondsToTimeString(milliseconds: Long): String {
        val simpleDateFormat = SimpleDateFormat("hh:mm:ss a")
        return simpleDateFormat.format(milliseconds)
    }
}