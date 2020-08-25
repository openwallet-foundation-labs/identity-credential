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

import android.util.Log
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.namespace.CborNamespace
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Request private constructor(
    val version: String?,
    val docRequests: List<DocRequest>
) : AbstractCborStructure(), IRequest {

    override fun getConsentRequestItems(): List<String>? {
        docRequests.forEach { doc ->
            doc.itemsRequest.namespaces.namespaces.forEach { namespace ->
                if (namespace.key == MdlNamespace.namespace) {
                    return namespace.value.dataElements.keys.filter { it != "portrait" }.toList()
                }
            }
        }
        return null
    }

    override fun isValid(): Boolean {
        if (version != null) {
            docRequests.forEach { doc ->
                //DocType is a optional element, so if it's not available,
                // we need to iterate over the namespaces
                doc.itemsRequest.doctype?.let {
                    if (it.docType != MdlDoctype.docType) {
                        return@forEach
                    }
                }

                doc.itemsRequest.namespaces.namespaces.forEach nested@{ entry ->
                    if (entry.key != MdlNamespace.namespace) {
                        return@nested
                    }

                    return entry.value.dataElements.keys.isNotEmpty()
                }
            }
        }
        return false
    }

    override fun getRequestParams(): List<ItemsRequest> {

        docRequests.forEach { doc ->
            doc.itemsRequest.namespaces.namespaces.forEach { namespace ->
                if (namespace.key == MdlNamespace.namespace) {
                    return mutableListOf(doc.itemsRequest)
                }
            }
        }

        return mutableListOf()
    }

    companion object {
        const val VERSION_KEY = "version"
        const val DOC_REQUESTS_KEY = "docRequests"
        const val VERSION = "1.0"
        val LOG_TAG = Request::class.java.simpleName
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var structureMap = builder.addMap()

        //Version
        structureMap = structureMap.put(VERSION_KEY, version)

        //DocRequests
        val arr = structureMap.putArray(DOC_REQUESTS_KEY)
        docRequests.forEach { item ->
            arr.add(item.toMap())
        }

        structureMap = arr.end()
        builder = structureMap.end()

        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    class Builder {
        private var version : String? = null
        private var docRequests = mutableListOf<DocRequest>()

        fun setVersion(version : String) = apply {
            this.version = version
        }

        fun addDocRequest(docRequest: DocRequest) = apply {
            docRequests.add(docRequest)
        }

        fun dataItemsToRequest(requestItems : kotlin.Array<String>?) = apply {
            requestItems?.let { ri ->
                val items = ri.map { it to false }.toMap()

                val dataElements = DataElements.Builder()
                    .dataElements(items)
                    .build()

                val namespacesRequest = CborNamespace.Builder()
                    .addNamespace(MdlNamespace.namespace, dataElements)
                    .build()

                val itemsRequest = ItemsRequest.Builder()
                    .namespaces(namespacesRequest)
                    .docType(MdlDoctype)
                    .build()

                val docRequest = DocRequest.Builder()
                    .itemsRequest(itemsRequest)
                    .build()

                docRequest?.let { doc ->
                    addDocRequest(doc)
                }
            }
            setVersion(VERSION)
        }

        fun decode(bytes : ByteArray) = apply {
            try {
                val bais = ByteArrayInputStream(bytes)
                val decoded = CborDecoder(bais).decode()
                if (decoded.size > 0) {
                    val structureItems: Map? = decoded[0] as? Map
                    structureItems?.let { struct ->
                        struct.keys.forEach {
                            val key: UnicodeString? = it as? UnicodeString
                            when (key?.string) {
                                VERSION_KEY -> {
                                    val valueVer = struct.get(it) as? UnicodeString
                                    valueVer?.let { ver ->
                                        version = ver.string
                                    }
                                }

                                DOC_REQUESTS_KEY -> {
                                    val docRequests = struct.get(it) as? Array
                                    docRequests?.let { reqs ->
                                        reqs.dataItems.forEach { item ->
                                            (item as? Map)?.let {
                                                DocRequest.Builder().decode(item).build()
                                                    ?.let { docRequest ->
                                                        this.docRequests.add(docRequest)
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (ex: CborException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
        }

        fun build() : Request {
            return Request(
                version,
                docRequests
            )
        }
    }
}