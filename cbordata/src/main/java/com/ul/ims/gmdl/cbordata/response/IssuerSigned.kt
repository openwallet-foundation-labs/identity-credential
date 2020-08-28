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

import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.response.DeviceSigned.Companion.KEY_NAMESPACES
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import java.io.ByteArrayOutputStream
import java.io.Serializable

class IssuerSigned private constructor(
    val nameSpaces: kotlin.collections.Map<String, kotlin.Array<IssuerSignedItem>>?,
    val issuerAuth: CoseSign1
) : AbstractCborStructure(), Serializable {

    companion object {
        const val KEY_NAME_SPACES = "nameSpaces"
        const val KEY_ISSUER_AUTH = "issuerAuth"
    }

    fun toDataItem(): DataItem {
        val map = Map()

        // NameSpaces
        nameSpaces?.let { ns ->
            val issuerNameSpaces = Map()
            ns.forEach { (key, value) ->
                val array = Array()
                value.forEach {
                    val byteString = ByteString(it.encode())
                    byteString.setTag(24)
                    array.add(byteString)
                }
                issuerNameSpaces.put(toDataItem(key), array)
            }
            map.put(toDataItem(KEY_NAME_SPACES), issuerNameSpaces)
        }

        // IssuerAuth
        map.put(toDataItem(KEY_ISSUER_AUTH), toDataItem(issuerAuth))

        return map
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(toDataItem())

        return outputStream.toByteArray()
    }

    class Builder {
        private var nameSpaces = mutableMapOf<String, kotlin.Array<IssuerSignedItem>>()
        private lateinit var issuerAuth: CoseSign1

        fun setNameSpaces(nameSpaces: String, issuerSignedItem: kotlin.Array<IssuerSignedItem>) =
            apply {
                this.nameSpaces[nameSpaces] = issuerSignedItem
            }

        fun setIssuerAuth(issuerAuth: CoseSign1) = apply {
            this.issuerAuth = issuerAuth
        }

        fun decode(map: Map?) = apply {
            map?.keys?.forEach { item ->
                val key: UnicodeString? = item as? UnicodeString
                when (key?.string) {
                    KEY_ISSUER_AUTH -> {
                        (map.get(item) as? Array)?.let {
                            setIssuerAuth(
                                CoseSign1.Builder()
                                .decode(it)
                                    .build()
                            )
                        }
                    }

                    KEY_NAMESPACES -> {
                        (map.get(item) as? Map)?.let { v ->
                            v.keys.forEach { item ->

                                (item as? UnicodeString)?.string?.let {
                                    val itemsList = arrayListOf<IssuerSignedItem>()

                                    (v.get(item) as? Array)?.let { a ->
                                        val dataItems = a.dataItems

                                        dataItems.forEach { di ->
                                            itemsList.add(
                                                IssuerSignedItem.Builder()
                                                    .decode((di as? ByteString)?.bytes)
                                                    .build()
                                            )
                                        }
                                    }

                                    setNameSpaces(it, itemsList.toTypedArray())
                                }
                            }
                        }
                    }
                }
            }
        }

        fun build(): IssuerSigned {
            return IssuerSigned(
                nameSpaces,
                issuerAuth
            )
        }
    }
}