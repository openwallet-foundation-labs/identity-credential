package com.android.mdl.app.fragment

import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
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
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.util.FormatUtil
import java.io.ByteArrayInputStream
import java.lang.StringBuilder
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class ShowDocumentDataFragment : Fragment() {

    private val arguments by navArgs<ShowDocumentDataFragmentArgs>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return TextView(requireContext()).apply {
            text = "Hello from Document Data"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //TODO here we'll load the document data
        val entries = DocumentManager.getInstance(requireContext()).showData(arguments.document)
        val sb = StringBuilder()
        entries?.namespaces?.forEach { ns ->
            sb.append("<br>")
            sb.append("<h5>Namespace: $ns</h5>")
            sb.append("<p>")
            for (elem in entries.getEntryNames(ns)) {
                val value: ByteArray? = entries.getEntry(ns, elem)
                value?.let {
                    var valueStr = cborPrettyPrint(value)
                    sb.append("<b>$elem</b> -> $valueStr<br>")
                }
//                if (doc.docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && elem == "portrait") {
//                    valueStr = String.format("(%d bytes, shown above)", value.size)
//                    portraitBytes = doc.getIssuerEntryByteString(ns, elem)
//                } else if (doc.docType == MICOV_DOCTYPE && ns == MICOV_ATT_NAMESPACE && elem == "fac") {
//                    valueStr = String.format("(%d bytes, shown above)", value.size)
//                    portraitBytes = doc.getIssuerEntryByteString(ns, elem)
//                } else if (doc.docType == MDL_DOCTYPE
//                    && ns == MDL_NAMESPACE && elem == "extra"
//                ) {
//                    valueStr = String.format("%d bytes extra data", value.size)
//                } else {
//                    valueStr = FormatUtil.cborPrettyPrint(value)
//                }
            }
            sb.append("</p><br>")
        }
        (view as TextView).text = Html.fromHtml(sb.toString())
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