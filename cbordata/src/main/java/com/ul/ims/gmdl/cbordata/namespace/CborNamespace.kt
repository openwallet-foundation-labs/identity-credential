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

package com.ul.ims.gmdl.cbordata.namespace

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.builder.ArrayBuilder
import co.nstant.`in`.cbor.builder.MapBuilder
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.request.DataElements
import com.ul.ims.gmdl.cbordata.request.ItemsRequest
import java.io.ByteArrayOutputStream

class CborNamespace private constructor(
    val namespaces: Map<String, DataElements>
): AbstractCborStructure() {

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()

        builder = buildCborBuilderStructure(builder)

        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    private fun buildCborBuilderStructure(builder: CborBuilder): CborBuilder {
        val structureMap = builder.addMap()

        namespaces.forEach {
            structureMap.put(UnicodeString(it.key), it.value.toCborMap())
        }

        return structureMap.end()
    }

    fun buildCborMapBuilderStructure(builder : MapBuilder<CborBuilder>, key: String) : MapBuilder<CborBuilder> {
        val structureMap = builder.putMap(key)

        namespaces.forEach {
            structureMap.put(UnicodeString(it.key), it.value.toCborMap())
        }

        return structureMap.end()
    }

    fun buildCborNestedStructure(
        builder: MapBuilder<MapBuilder<ArrayBuilder<MapBuilder<CborBuilder>>>>
    ): MapBuilder<MapBuilder<ArrayBuilder<MapBuilder<CborBuilder>>>> {
        val structureMap = builder.putMap(ItemsRequest.NAMESPACE_KEY)

        namespaces.forEach {
            structureMap.put(UnicodeString(it.key), it.value.toCborMap())
        }

        return structureMap.end()
    }

    class Builder {
        private var namespaces: MutableMap<String, DataElements> = linkedMapOf()

        fun addNamespace(namespace: String, dataElements: DataElements) = apply {
            namespaces[namespace] = dataElements
        }

        fun decode(map: co.nstant.`in`.cbor.model.Map) = apply {
            map.keys.forEach {
                val namespace: UnicodeString? = it as? UnicodeString
                namespace?.let {
                    val arr = map.get(namespace) as? co.nstant.`in`.cbor.model.Map
                    arr?.let { obj ->
                        namespaces[namespace.string] = DataElements.Builder().decode(obj).build()
                    }
                }
            }
        }

        fun build() : CborNamespace {
            return CborNamespace(namespaces)
        }
    }
}