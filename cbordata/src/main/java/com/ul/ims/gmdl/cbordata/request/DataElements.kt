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

package com.ul.ims.gmdl.cbordata.request

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.SimpleValue
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import java.io.ByteArrayOutputStream

class DataElements private constructor(
    val dataElements: Map<String, Boolean>
) : AbstractCborStructure() {

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        val structureMap = builder.addMap()

        dataElements.entries.forEach {
            structureMap.put(it.key, it.value)
        }

        builder = structureMap.end()

        CborEncoder(outputStream).nonCanonical().encode(builder.build())
        return outputStream.toByteArray()
    }

    fun toCborMap(): co.nstant.`in`.cbor.model.Map {
        val map = co.nstant.`in`.cbor.model.Map()

        dataElements.entries.forEach {
            map.put(UnicodeString(it.key), convert(it.value))
        }

        return map
    }

    class Builder {
        private var dataElements: Map<String, Boolean> = linkedMapOf()

        fun dataElements(namespaces: Map<String, Boolean>) = apply {
            this.dataElements = namespaces
        }

        fun decode(dataItem: DataItem) = apply {
            (dataItem as? co.nstant.`in`.cbor.model.Map)?.let {
                val map = linkedMapOf<String, Boolean>()
                dataItem.keys.forEach { key ->
                    (key as? UnicodeString)?.let { string ->
                        (dataItem[key] as? SimpleValue)?.let { simpleValue ->
                            map.put(string.string, convert(simpleValue))
                        }
                    }
                }
                this.dataElements = map
            }
        }


        private fun convert(value: SimpleValue): Boolean {
            return value == SimpleValue.TRUE
        }

        fun build(): DataElements {
            return DataElements(dataElements)
        }
    }

    private fun convert(value: Boolean): DataItem {
        return if (value) {
            SimpleValue.TRUE
        } else {
            SimpleValue.FALSE
        }
    }


}