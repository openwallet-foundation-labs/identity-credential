package com.android.identity.util

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
import co.nstant.`in`.cbor.model.UnsignedInteger
import com.android.identity.internal.Util
import com.android.identity.util.Logger.w
import java.io.ByteArrayInputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CborUtil {
    private const val TAG = "CborUtil"

    const val DIAGNOSTICS_FLAG_EMBEDDED_CBOR = 1 shl 0
    const val DIAGNOSTICS_FLAG_PRETTY_PRINT = 1 shl 1

    // Returns true iff all elements in |items| are not compound (e.g. an array or a map).
    private fun cborAreAllDataItemsNonCompound(
        items: List<DataItem>,
        flags: Int
    ): Boolean {
        for (item in items) {
            if (flags and DIAGNOSTICS_FLAG_EMBEDDED_CBOR != 0 &&
                item.hasTag() &&
                item.tag.value == 24L
            ) {
                return false
            }
            when (item.majorType) {
                MajorType.ARRAY, MajorType.MAP -> return false
                else -> {}
            }
        }
        return true
    }

    private fun cborFitsInSingleLine(
        items: List<DataItem>,
        flags: Int
    ): Boolean {
        // For now just use this heuristic.
        return cborAreAllDataItemsNonCompound(items, flags) && items.size < 8
    }

    private fun toDiagnostics(
        sb: StringBuilder,
        indent: Int,
        dataItem: DataItem,
        flags: Int
    ) {
        var count: Int
        val pretty = flags and DIAGNOSTICS_FLAG_PRETTY_PRINT != 0
        var indentString = ""
        if (pretty) {
            val indentBuilder = StringBuilder()
            for (n in 0 until indent) {
                indentBuilder.append(' ')
            }
            indentString = indentBuilder.toString()
        }
        if (dataItem.hasTag()) {
            sb.append(String.format(Locale.US, "%d(", dataItem.tag.value))
        }
        when (dataItem.majorType) {
            MajorType.INVALID -> sb.append("<invalid>")
            MajorType.UNSIGNED_INTEGER ->
                // Major type 0: an unsigned integer.
                sb.append((dataItem as UnsignedInteger).value)

            MajorType.NEGATIVE_INTEGER ->
                // Major type 1: a negative integer.
                sb.append((dataItem as NegativeInteger).value)

            MajorType.BYTE_STRING -> {
                // Major type 2: a byte string.
                val bstrValue = (dataItem as ByteString).bytes
                if (dataItem.hasTag() &&
                    dataItem.getTag().value == 24L &&
                    flags and DIAGNOSTICS_FLAG_EMBEDDED_CBOR != 0
                ) {
                    sb.append("<< ")
                    val bais = ByteArrayInputStream(bstrValue)
                    val dataItems: List<DataItem>?
                    try {
                        dataItems = CborDecoder(bais).decode()
                        if (dataItems.isNotEmpty()) {
                            toDiagnostics(sb, indent, Util.cborDecode(bstrValue), flags)
                            if (dataItems.size > 1) {
                                w(
                                    TAG, "Multiple data items in embedded CBOR, "
                                            + "only printing the first"
                                )
                                sb.append(
                                    String.format(
                                        Locale.US,
                                        " Error: omitting %d additional items",
                                        dataItems.size - 1
                                    )
                                )
                            }
                        } else {
                            sb.append("Error: 0 Data Items")
                        }
                    } catch (e: CborException) {
                        // Never throw an exception
                        sb.append("Error Decoding CBOR")
                        w(TAG, "Error decoding CBOR: $e")
                    }
                    sb.append(" >>")
                } else {
                    sb.append("h'")
                    count = 0
                    for (b in bstrValue) {
                        sb.append(String.format(Locale.US, "%02x", b))
                        count++
                    }
                    sb.append("'")
                }
            }

            MajorType.UNICODE_STRING -> {
                // Major type 3: string of Unicode characters that is encoded as UTF-8 [RFC3629].
                val strValue = Util.checkedStringValue(dataItem)
                val escapedStrValue = strValue.replace("\"", "\\\"")
                sb.append("\"" + escapedStrValue + "\"")
            }

            MajorType.ARRAY -> {
                // Major type 4: an array of data items.
                val items: List<DataItem> = (dataItem as Array).dataItems
                if (!pretty || cborFitsInSingleLine(items, flags)) {
                    sb.append("[")
                    count = 0
                    for (item in items) {
                        toDiagnostics(sb, indent, item, flags)
                        if (++count < items.size) {
                            sb.append(", ")
                        }
                    }
                    sb.append("]")
                } else {
                    sb.append("[\n").append(indentString)
                    count = 0
                    for (item in items) {
                        sb.append("  ")
                        toDiagnostics(sb, indent + 2, item, flags)
                        if (++count < items.size) {
                            sb.append(",")
                        }
                        sb.append("\n").append(indentString)
                    }
                    sb.append("]")
                }
            }

            MajorType.MAP -> {
                // Major type 5: a map of pairs of data items.
                val keys: Collection<DataItem> = (dataItem as Map).keys
                if (!pretty || keys.isEmpty()) {
                    sb.append("{")
                    count = 0
                    for (key in keys) {
                        val value: DataItem = dataItem[key]
                        toDiagnostics(sb, indent, key, flags)
                        sb.append(": ")
                        toDiagnostics(sb, indent + 2, value, flags)
                        if (++count < keys.size) {
                            sb.append(", ")
                        }
                    }
                    sb.append("}")
                } else {
                    sb.append("{\n").append(indentString)
                    count = 0
                    for (key in keys) {
                        sb.append("  ")
                        val value: DataItem = dataItem[key]
                        toDiagnostics(sb, indent + 2, key, flags)
                        sb.append(": ")
                        toDiagnostics(sb, indent + 2, value, flags)
                        if (++count < keys.size) {
                            sb.append(",")
                        }
                        sb.append("\n").append(indentString)
                    }
                    sb.append("}")
                }
            }

            MajorType.TAG -> {}
            
            MajorType.SPECIAL ->
                // Major type 7: floating point numbers and simple data types that need no
                // content, as well as the "break" stop code.
                if (dataItem is SimpleValue) {
                    when (dataItem.simpleValueType) {
                        SimpleValueType.FALSE -> sb.append("false")
                        SimpleValueType.TRUE -> sb.append("true")
                        SimpleValueType.NULL -> sb.append("null")
                        SimpleValueType.UNDEFINED -> sb.append("undefined")
                        SimpleValueType.RESERVED -> sb.append("reserved")
                        SimpleValueType.UNALLOCATED -> {
                            sb.append("simple(")
                            sb.append(dataItem.getValue())
                            sb.append(")")
                        }
                    }
                } else if (dataItem is DoublePrecisionFloat) {
                    val df = DecimalFormat(
                        "0",
                        DecimalFormatSymbols.getInstance(Locale.ENGLISH)
                    )
                    df.maximumFractionDigits = 340
                    sb.append(df.format(dataItem.getValue()))
                } else if (dataItem is AbstractFloat) {
                    val df = DecimalFormat(
                        "0",
                        DecimalFormatSymbols.getInstance(Locale.ENGLISH)
                    )
                    df.maximumFractionDigits = 340
                    sb.append(df.format(dataItem.getValue().toDouble()))
                } else {
                    sb.append("break")
                }
        }
        if (dataItem.hasTag()) {
            sb.append(")")
        }
    }

    @JvmStatic
    @JvmOverloads
    fun toDiagnostics(dataItem: DataItem, flags: Int = 0): String {
        val sb = StringBuilder()
        toDiagnostics(sb, 0, dataItem, flags)
        return sb.toString()
    }

    @JvmStatic
    @JvmOverloads
    fun toDiagnostics(encodedCbor: ByteArray, flags: Int = 0): String {
        val sb = StringBuilder()
        val bais = ByteArrayInputStream(encodedCbor)
        val dataItems = try {
            CborDecoder(bais).decode()
        } catch (e: CborException) {
            // Never throw an exception
            return "Error Decoding CBOR"
        }
        var count = 0
        for (dataItem in dataItems) {
            if (count > 0) {
                sb.append(",\n")
            }
            toDiagnostics(sb, 0, dataItem, flags)
            count++
        }
        return sb.toString()
    }
}