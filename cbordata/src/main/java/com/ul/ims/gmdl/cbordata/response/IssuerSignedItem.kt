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

package com.ul.ims.gmdl.cbordata.response

import android.icu.util.Calendar
import android.util.Log
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.MdlDataIdentifiers
import com.ul.ims.gmdl.cbordata.drivingPrivileges.DrivingPrivileges
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructureBuilder
import com.ul.ims.gmdl.cbordata.utils.Base64Utils
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.cbordata.utils.DateUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.zip.DataFormatException

class IssuerSignedItem private constructor(
        val digestId: Int,
        val randomValue: ByteArray,
        val elementIdentifier: String,
        val elementValue: Any
) : AbstractCborStructure() , Serializable {

    companion object {
        const val LOG_TAG = "IssuerSignedItem"
        const val KEY_DIGEST_ID = "digestID"
        const val KEY_RANDOM_VALUE = "random"
        const val KEY_ELEMENT_IDENTIFIER = "elementIdentifier"
        const val KEY_ELEMENT_VALUE = "elementValue"
    }

    override fun encode(): ByteArray {
        val map = toDataItem()

        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).nonCanonical().encode(map)

        return outputStream.toByteArray()
    }

    fun toDataItem(): DataItem {
        val map = Map()

        // Digest Id
        map.put(toDataItem(KEY_DIGEST_ID), toDataItem(digestId))

        // Random value
        map.put(toDataItem(KEY_RANDOM_VALUE), ByteString(randomValue))

        // Element identifier
        map.put(toDataItem(KEY_ELEMENT_IDENTIFIER), toDataItem(elementIdentifier))

        // Element Value
        if (elementIdentifier == MdlDataIdentifiers.DATE_OF_BIRTH.identifier) {
            map.put(
                toDataItem(KEY_ELEMENT_VALUE),
                CborUtils.dateToUnicodeString(elementValue as? Calendar)
            )

        } else {
            map.put(toDataItem(KEY_ELEMENT_VALUE), toDataItem(elementValue))
        }

        return map
    }

    class Builder : AbstractCborStructureBuilder() {

        private var digestId: Int = 0
        private var randomValue: ByteArray = byteArrayOf()
        private var elementIdentifier: String = ""
        private var elementValue: Any = Any()

        fun setDigestId(digest: Int) = apply {
            digestId = digest
        }

        fun setRandomValue(value: ByteArray) = apply {
            randomValue = value
        }

        fun setElementIdentifier(identifier: String) = apply {
            elementIdentifier = identifier
        }

        fun setElementValue(elementVal: Any) = apply {
            elementValue = elementVal
        }

        fun build() = IssuerSignedItem(
            digestId,
            randomValue,
            elementIdentifier,
            elementValue
        )

        fun fromBase64(response : String?) = apply {
            val bytes: ByteArray?
                if (response?.isEmpty() == false) {
                    try {
                        bytes = Base64Utils.decode(response)
                        decode(bytes)
                    } catch(ex: IllegalArgumentException) {
                        Log.e(LOG_TAG, ex.message, ex)
                    }
                }
        }

        fun decode(map: Map?) = apply {
            map?.let { m ->
                // digestId
                digestId = (m[UnicodeString(KEY_DIGEST_ID)] as UnsignedInteger).value.toInt()

                // randomValue
                randomValue = (m[UnicodeString(KEY_RANDOM_VALUE)] as ByteString).bytes

                // elementIdentifier
                elementIdentifier =
                    (m[UnicodeString(KEY_ELEMENT_IDENTIFIER)] as UnicodeString).string

                //elementValue
                elementValue = decodeElementValue(m[UnicodeString(KEY_ELEMENT_VALUE)])
            }
        }

        fun decode(bytes: ByteArray?) = apply {
            try {
                val bais = ByteArrayInputStream(bytes)
                val decoded = CborDecoder(bais).decode()
                if (decoded.size > 0) {
                    val structureItems: Map? = decoded[0] as? Map
                    decode(structureItems)
                }
            } catch (ex: CborException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
        }

        private fun decodeElementValue(cborDataItem: DataItem): Any {
            val majorType = cborDataItem.majorType
            val tag = cborDataItem.tag

            if ((tag?.value == 0L || tag?.value == 18013L) && majorType == MajorType.UNICODE_STRING) {
                return decodeAsCalendar(cborDataItem)
            }

            if (elementIdentifier == DrivingPrivileges.DRIVING_PRIVILEGES) {
                return decodeDrivingPrivileges(cborDataItem)
            }

            return when (majorType) {
                MajorType.UNICODE_STRING -> cborDataItem.toString()
                MajorType.SPECIAL -> {
                    return when ((cborDataItem as SimpleValue).simpleValueType) {
                        SimpleValueType.TRUE -> true
                        SimpleValueType.FALSE -> false
                        else -> throw DataFormatException("Invalid IssuerSignedItem. $cborDataItem")
                    }
                }
                MajorType.MAP -> cborDataItem
                MajorType.BYTE_STRING -> (cborDataItem as ByteString).bytes
                else -> throw RuntimeException(
                    "decoding of $majorType type is not implemented yet"
                )
            }
        }

        private fun decodeDrivingPrivileges(dpDataItem: DataItem?) : DrivingPrivileges {
            val builder = DrivingPrivileges.Builder()

            dpDataItem?.let {
                builder.fromCborDataItem(it)
            }

            return builder.build()
        }

        private fun decodeAsCalendar(cborDataItem: DataItem): Calendar {
            return DateUtils.cborDecodeCalendar(
                decodeUnicodeString(
                    cborDataItem
                )
            )
        }

        private fun decodeUnicodeString(dataItem: DataItem): String {
            if (dataItem.majorType == MajorType.UNICODE_STRING) {
                return (dataItem as UnicodeString).string
            }

            throw RuntimeException("DataItem is not a UnicodeString Cbor")
        }
    }
}