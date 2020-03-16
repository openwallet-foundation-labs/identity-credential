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

package com.ul.ims.gmdl.cbordata.security

import android.util.Log
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.response.IssuerSignedItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

class IssuerNameSpaces private constructor
    (val nameSpaces: MutableMap<String, MutableList<IssuerSignedItem>>)
    : Serializable, AbstractCborStructure() {

    companion object {
        const val LOG_TAG = "IssuerNameSpaces"
    }

    class Builder{
        private var listOfIssuerSignedItems: MutableList<IssuerSignedItem> = mutableListOf()
        private var listOfNameSpaces: MutableMap<String, MutableList<IssuerSignedItem>> = mutableMapOf()

        fun decode(data: ByteArray) = apply {
            try {
                val stream = ByteArrayInputStream(data)
                val dataItems = CborDecoder(stream).decode()
                if (dataItems.size > 0 ) {
                    val structureItems: Map? = dataItems[0] as? Map
                    structureItems?.let {
                        if (it.keys.isNotEmpty()) {
                            decodeNameSpaces(it)
                        }
                    }
                }
            } catch (ex: CborException) {
                Log.e(javaClass.simpleName,": $ex")
            }
        }

        fun setNamespace(namespace : String, issuerSignedItems : MutableList<IssuerSignedItem>) = apply {
            listOfNameSpaces.put(namespace, issuerSignedItems.toMutableList())
        }

        private fun decodeNameSpaces(nameSpaces: Map) {
            nameSpaces.keys.forEach { i ->
                i as UnicodeString
                try {
                    val arrayOfIssuerSignedItems = nameSpaces.get(i) as Array
                    arrayOfIssuerSignedItems.dataItems.forEach { j ->
                        val isi = IssuerSignedItem.Builder().decode(j as Map).build()
                        listOfIssuerSignedItems.add(isi)
                    }
                    listOfNameSpaces[i.string] = listOfIssuerSignedItems
                } catch (ex: CborException) {
                    Log.e(LOG_TAG, ex.message.toString())
                }
            }
        }

        fun build() : IssuerNameSpaces {
            return IssuerNameSpaces(listOfNameSpaces)
        }
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val builder = CborBuilder()
        val structureMap = builder.addMap()
        
        nameSpaces.forEach { (ns, l) ->
            var isiArr = Array()
            l.forEach {
                isiArr = isiArr.add(toDataItem(it))
            }

            structureMap.put(UnicodeString(ns), isiArr)
        }

        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }
}