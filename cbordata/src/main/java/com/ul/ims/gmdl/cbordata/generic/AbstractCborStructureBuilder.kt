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

package com.ul.ims.gmdl.cbordata.generic

import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.ICborStructureBuilder

abstract class AbstractCborStructureBuilder : ICborStructureBuilder {
    // Convert from a cbor specific type to a kotlin type
    override fun fromDataItem(dataItem: DataItem): Any {
        return when (dataItem.majorType) {
            MajorType.UNSIGNED_INTEGER -> (dataItem as UnsignedInteger).value.toInt()
            MajorType.UNICODE_STRING -> (dataItem as UnicodeString).string
            MajorType.BYTE_STRING -> (dataItem as ByteString).bytes
            else -> dataItem
        }
    }

    fun asMap(cborDataItems : MutableList<DataItem>, position: Int) : Map {
        var optionsMap = Map()
        val elementValues = cborDataItems.getOrNull(position) as? Map
        elementValues?.let {
            optionsMap = it
        }

        return optionsMap
    }

    fun asString(cborDataItems : MutableList<DataItem>, position: Int) : String {
        var elementValue = UnicodeString("")
        val elementValues = cborDataItems.getOrNull(position) as? UnicodeString
        elementValues?.let {
            elementValue = it
        }
        return elementValue.toString()
    }

    fun asBoolean(cborDataItems: MutableList<DataItem>, position: Int) : Boolean {
        val elementValues = cborDataItems.getOrNull(position) as? SimpleValue
        elementValues?.let {
            return when (elementValues.simpleValueType) {
                SimpleValueType.TRUE -> true
                SimpleValueType.FALSE -> false
                // TODO: Check if it's needed to throw specific exception
                else -> throw RuntimeException("Invalid age_over_NN IssuerSignedItem.")
            }
        }
        return false
    }

    fun asByteString(cborDataItems: MutableList<DataItem>, position: Int) : ByteArray {
        var byteString = ByteString(null)
        val byteStrings = cborDataItems.getOrNull(position) as? ByteString
        byteStrings?.let {
            byteString = it
        }
        return byteString.bytes
    }
}