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
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.builder.ArrayBuilder
import co.nstant.`in`.cbor.builder.MapBuilder
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.doctype.DocType
import com.ul.ims.gmdl.cbordata.doctype.IDoctype
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.namespace.CborNamespace
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ItemsRequest private constructor(
    val doctype : IDoctype?,
    val namespaces : CborNamespace,
    val requestInfo: MutableMap<String, Any>?
) : AbstractCborStructure() {

    companion object {
        const val DOCTYPE_KEY = "docType"
        const val NAMESPACE_KEY = "nameSpaces"
        const val REQUEST_INFO_KEY = "RequestInfo"
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var structureMap = builder.addMap()

        doctype?.let {
            structureMap = structureMap.put(DOCTYPE_KEY, it.docType)
        }

        structureMap = namespaces.buildCborMapBuilderStructure(structureMap,
            NAMESPACE_KEY
        )

        requestInfo?.let {
            if (requestInfo.isNotEmpty()) {
                var requestInfoMapBuilder = structureMap.putMap(REQUEST_INFO_KEY)
                requestInfo.forEach { (key, value) ->
                    requestInfoMapBuilder =
                        requestInfoMapBuilder.put(toDataItem(key), toDataItem(value))
                }
                structureMap = requestInfoMapBuilder.end()
            }
        }

        builder = structureMap.end()

        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    fun buildCborStructure(builder : MapBuilder<ArrayBuilder<MapBuilder<CborBuilder>>>,
                           key : String?) : MapBuilder<ArrayBuilder<MapBuilder<CborBuilder>>> {
        var structureMap = builder.putMap(key)

        doctype?.let {
            structureMap = structureMap.put(DOCTYPE_KEY, it.docType)
        }

        structureMap = namespaces.buildCborNestedStructure(
            structureMap
        )

        return structureMap.end()
    }

    class Builder {
        private var doctype : IDoctype? = null
        private var namespaces : CborNamespace? = null
        private var requestInfo: MutableMap<String, Any>? = null

        fun docType(doctype : IDoctype?) = apply {
            this.doctype = doctype
        }

        fun namespaces(namespaces : CborNamespace) = apply {
            this.namespaces = namespaces
        }

        fun requestInfo(requestInfo: MutableMap<String, Any>) = apply {
            this.requestInfo = requestInfo
        }

        fun decode(bytes: ByteArray) = apply {
            val items = CborDecoder(ByteArrayInputStream(bytes)).decode()

            if (items.size == 1) {
                val structureItems = items[0] as? Map
                structureItems?.let {
                    decode(it)
                }
            }
        }

        fun decode(map : Map) = apply {
            map.keys.forEach {
                val key: UnicodeString? = it as? UnicodeString
                    when (key?.string) {
                        DOCTYPE_KEY -> {
                            val docType = map.get(it) as? UnicodeString
                            docType?.let { doc ->
                                doctype = DocType(docType = doc.string)
                            }
                        }
                        NAMESPACE_KEY -> {
                            val namespaces = map.get(it) as? Map
                            namespaces?.let { obj ->
                                this.namespaces = CborNamespace.Builder()
                                        .decode(obj)
                                        .build()
                            }
                        }
                    }
            }
        }

        fun build(): ItemsRequest? {
            namespaces?.let {
                return ItemsRequest(
                    doctype,
                    it,
                    requestInfo
                )
            }
            return null
        }
    }
}