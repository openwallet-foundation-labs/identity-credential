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
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import java.io.ByteArrayOutputStream

class DocRequest private constructor(
    val itemsRequest: ItemsRequest,
    private val readerAuth: CoseSign1?

) : AbstractCborStructure() {

    companion object {
        const val ITEMSREQUEST_KEY = "itemsRequest"
        const val READERAUTH_KEY = "readerAuth"
    }

    override fun encode(): ByteArray {

        var builder = CborBuilder()
        val mapBuilder = builder.addMap()

        val itemsRequestBytes = ByteString(itemsRequest.encode())
        itemsRequestBytes.setTag(24)
        mapBuilder.put(UnicodeString(ITEMSREQUEST_KEY), itemsRequestBytes)

        readerAuth?.let {
            mapBuilder.put(UnicodeString(READERAUTH_KEY), it.addToNestedStructure())
        }

        builder = mapBuilder.end()

        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(builder.build())

        return outputStream.toByteArray()
    }

    fun toMap(): co.nstant.`in`.cbor.model.Map {

        val map = co.nstant.`in`.cbor.model.Map()

        val itemsRequestBytes = ByteString(itemsRequest.encode())
        itemsRequestBytes.setTag(24)
        map.put(UnicodeString(ITEMSREQUEST_KEY), itemsRequestBytes)

        readerAuth?.let {
            map.put(UnicodeString(READERAUTH_KEY), it.addToNestedStructure())
        }

        return map
    }


    class Builder {
        private var itemsRequest: ItemsRequest? = null
        private var readerAuth: CoseSign1? = null

        fun itemsRequest(itemsRequest: ItemsRequest?) = apply {
            this.itemsRequest = itemsRequest
        }

        fun readerAuth(readerAuth: CoseSign1?) = apply {
            this.readerAuth = readerAuth
        }

        fun decode(map: co.nstant.`in`.cbor.model.Map) = apply {
            map.keys.forEach {
                val key: UnicodeString? = it as? UnicodeString
                when (key?.string) {
                    ITEMSREQUEST_KEY -> {
                        val itemsRequest = map.get(it) as? ByteString
                        itemsRequest?.let { obj ->
                            this.itemsRequest = ItemsRequest.Builder()
                                .decode(obj.bytes)
                                .build()
                        }
                    }
                    READERAUTH_KEY -> {

                        val readerAuth = map.get(it) as? Array
                        readerAuth?.let { obj ->
                            this.readerAuth = CoseSign1.Builder().decode(obj).build()
                        }
                    }
                }
            }
        }

        fun build(): DocRequest? {
            itemsRequest?.let {
                return DocRequest(it, readerAuth)
            }
            return null
        }
    }
}