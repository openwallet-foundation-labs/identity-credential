/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.cbordata.utils

import android.icu.util.Calendar
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.*
import java.io.ByteArrayInputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

object CborUtils {
    const val TAG = "CborUtils"

    // Helper function to display a cbor structure in HEX
    fun encodeToString(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }

        return sb.toString()
    }

    // Helper function used to generate formatted data for junit tests
    fun stringToStringDebug(string: String): String {

        val sb = StringBuilder(string.length / 2 * 15)

        var newLineCounter = 0
        for (i in string.indices step 2) {
            sb.append("0x${string[i]}${string[i + 1]}.toByte()")

            if ((i + 1) < string.length) {
                newLineCounter++
                sb.append(", ")
                if (newLineCounter == 5) {
                    sb.append("\n")
                    newLineCounter = 0
                }
            }
        }

        return sb.toString()

    }

    // Helper function used to generate formatted data for junit tests
    fun encodeToStringDebug(encoded: ByteArray): String {
        val sb = StringBuilder(encoded.size * 2)

        val iterator = encoded.iterator().withIndex()
        var newLineCounter = 0
        iterator.forEach { b ->
            sb.append("0x")
            sb.append(String.format("%02x", b.value))
            sb.append(".toByte()")

            if (iterator.hasNext()) {
                newLineCounter++
                sb.append(", ")

                if (newLineCounter == 5) {
                    sb.append("\n")
                    newLineCounter = 0
                }
            }
        }

        return sb.toString()
    }

    fun dateTimeToUnicodeString(calendar: Calendar?): DataItem {
        val dateString = DateUtils.getFormattedDateTime(calendar)

        val unicodeString = UnicodeString(dateString)
        unicodeString.tag = Tag(0)

        return unicodeString
    }

    fun dateToUnicodeString(calendar: Calendar?): DataItem {
        val dateString = DateUtils.getFormattedDate(calendar)

        val unicodeString = UnicodeString(dateString)
        unicodeString.tag = Tag(18013)

        return unicodeString
    }

    @Throws(CborException::class)
    fun cborPrettyPrint(encodedBytes: ByteArray): String {
        val sb = StringBuilder()

        val bais = ByteArrayInputStream(encodedBytes)
        val dataItems = CborDecoder(bais).decode()
        for ((count, dataItem) in dataItems.withIndex()) {
            if (count > 0) {
                sb.append(",\n")
            }
            cborPrettyPrintDataItem(sb, 0, dataItem)
        }

        return sb.toString()
    }

    // Returns true iff all elements in |items| are not compound (e.g. an array or a map).
    private fun cborAreAllDataItemsNonCompound(items: List<DataItem>): Boolean {
        for (item in items) {
            when (item.majorType) {
                MajorType.ARRAY, MajorType.MAP -> return false
            }
        }
        return true
    }

    private fun cborPrettyPrintDataItem(sb: StringBuilder, indent: Int, dataItem: DataItem) {
        val indentBuilder = StringBuilder()
        for (n in 0 until indent) {
            indentBuilder.append(' ')
        }
        val indentString = indentBuilder.toString()

        if (dataItem.hasTag()) {
            sb.append(String.format("tag %d ", dataItem.tag.value))
        }

        when (dataItem.majorType) {
            MajorType.INVALID ->
                // TODO: throw
                sb.append("<invalid>")
            MajorType.UNSIGNED_INTEGER -> {
                // Major type 0: an unsigned integer.
                val value = (dataItem as UnsignedInteger).value
                sb.append(value)
            }
            MajorType.NEGATIVE_INTEGER -> {
                // Major type 1: a negative integer.
                val value = (dataItem as NegativeInteger).value
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
                val items = (dataItem as co.nstant.`in`.cbor.model.Array).dataItems
                when {
                    items.size == 0 -> {
                        sb.append("[]")
                    }
                    cborAreAllDataItemsNonCompound(items) -> {
                        // The case where everything fits on one line.
                        sb.append("[")
                        for ((count, item) in items.withIndex()) {
                            cborPrettyPrintDataItem(sb, indent, item)
                            if (count + 1 < items.size) {
                                sb.append(", ")
                            }
                        }
                        sb.append("]")
                    }
                    else -> {
                        sb.append("[\n$indentString")
                        for ((count, item) in items.withIndex()) {
                            sb.append("  ")
                            cborPrettyPrintDataItem(sb, indent + 2, item)
                            if (count + 1 < items.size) {
                                sb.append(",")
                            }
                            sb.append("\n" + indentString)
                        }
                        sb.append("]")
                    }
                }
            }
            MajorType.MAP -> {
                // Major type 5: a map of pairs of data items.
                val keys = (dataItem as co.nstant.`in`.cbor.model.Map).keys
                if (keys.isEmpty()) {
                    sb.append("{}")
                } else {
                    sb.append("{\n$indentString")
                    for ((count, key) in keys.withIndex()) {
                        sb.append("  ")
                        val value = dataItem.get(key)
                        cborPrettyPrintDataItem(sb, indent + 2, key)
                        sb.append(" : ")
                        cborPrettyPrintDataItem(sb, indent + 2, value)
                        if (count + 1 < keys.size) {
                            sb.append(",")
                        }
                        sb.append("\n" + indentString)
                    }
                    sb.append("}")
                }
            }
            MajorType.TAG ->
                // Major type 6: optional semantic tagging of other major types
                //
                // We never encounter this one since it's automatically handled via the
                // DataItem that is tagged.
                throw RuntimeException("Semantic tag data item not expected")

            MajorType.SPECIAL ->
                // Major type 7: floating point numbers and simple data types that need no
                // content, as well as the "break" stop code.
                when (dataItem) {
                    is SimpleValue -> {
                        when (dataItem.simpleValueType) {
                            SimpleValueType.FALSE -> sb.append("false")
                            SimpleValueType.TRUE -> sb.append("true")
                            SimpleValueType.NULL -> sb.append("null")
                            SimpleValueType.UNDEFINED -> sb.append("undefined")
                            SimpleValueType.RESERVED -> sb.append("reserved")
                            SimpleValueType.UNALLOCATED -> sb.append("unallocated")
                        }
                    }
                    is DoublePrecisionFloat -> {
                        val df = DecimalFormat(
                            "0",
                            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
                        )
                        df.maximumFractionDigits = 340
                        sb.append(df.format(dataItem.value))
                    }
                    is AbstractFloat -> {
                        val df = DecimalFormat(
                            "0",
                            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
                        )
                        df.maximumFractionDigits = 340
                        sb.append(df.format(dataItem.value.toDouble()))
                    }
                    else -> {
                        sb.append("break")
                    }
                }
        }
    }
}