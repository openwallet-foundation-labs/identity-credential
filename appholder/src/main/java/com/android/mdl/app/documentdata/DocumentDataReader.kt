package com.android.mdl.app.documentdata

import android.graphics.BitmapFactory
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
import com.android.identity.CredentialDataResult
import com.android.identity.CredentialDataResult.Entries.STATUS_USER_AUTHENTICATION_FAILED
import com.android.mdl.app.document.Document
import com.android.mdl.app.util.DocumentData.MDL_DOCTYPE
import com.android.mdl.app.util.DocumentData.MDL_NAMESPACE
import com.android.mdl.app.util.DocumentData.MICOV_ATT_NAMESPACE
import com.android.mdl.app.util.DocumentData.MICOV_DOCTYPE
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class DocumentDataReader(private val entries: CredentialDataResult.Entries) {

    fun read(document: Document): DocumentElements {
        val missingAuth = entries.namespaces.any { namespace ->
            entries.getEntryNames(namespace).any { entry ->
                entries.getStatus(namespace, entry) == STATUS_USER_AUTHENTICATION_FAILED
            }
        }
        if (missingAuth) {
            return DocumentElements(requestUserAuthorization = true)
        }

        val builder = StringBuilder()
        var portraitBytes: ByteArray? = null
        var signatureBytes: ByteArray? = null
        val docType = document.docType
        entries.namespaces.forEach { ns ->
            builder.append("<br>")
            builder.append("<h5>Namespace: $ns</h5>")
            builder.append("<p>")
            entries.getEntryNames(ns).forEach { entryName ->
                val byteArray: ByteArray? = entries.getEntry(ns, entryName)
                byteArray?.let { value ->
                    val valueStr: String
                    if (docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && entryName == "portrait") {
                        valueStr = String.format("(%d bytes, shown above)", value.size)
                        portraitBytes = entries.getEntryBytestring(ns, entryName)
                    } else if (docType == MICOV_DOCTYPE && ns == MICOV_ATT_NAMESPACE && entryName == "fac") {
                        valueStr = String.format("(%d bytes, shown above)", value.size)
                        portraitBytes = entries.getEntryBytestring(ns, entryName)
                    } else if (docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && entryName == "extra") {
                        valueStr = String.format("%d bytes extra data", value.size)
                    } else if (docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && entryName == "signature_usual_mark") {
                        valueStr = String.format("(%d bytes, shown below)", value.size)
                        signatureBytes = entries.getEntryBytestring(ns, entryName)
                    } else {
                        valueStr = cborPrettyPrint(value)
                    }
                    builder.append("<b>$entryName</b> -> $valueStr<br>")
                }
            }
            builder.append("</p><br>")
        }
        val portrait = portraitBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        val signature = signatureBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        return DocumentElements(builder.toString(), portrait = portrait, signature = signature)
    }

    private fun cborPrettyPrint(encodedBytes: ByteArray): String {
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