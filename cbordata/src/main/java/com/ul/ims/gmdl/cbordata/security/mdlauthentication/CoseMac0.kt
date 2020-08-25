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

package com.ul.ims.gmdl.cbordata.security.mdlauthentication

import android.util.Log
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructureBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

class CoseMac0 private constructor(val payload : ByteArray?,
                                   val macValue : ByteArray?
) : AbstractCborStructure(), Serializable {
    companion object {
        private const val LOG_TAG = "CoseMac0"
        private val HMAC256_ALGORITHM_LABEL = UnsignedInteger(1)
        private const val HMAC256_ALGORITHM_VALUE : Long = 5
        const val HMAC256_ALGORITHM_NAME : String = "HMac/SHA256"
    }

    var algorithm = HMAC256_ALGORITHM_NAME

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var arrayBuilder = builder.addArray()
        arrayBuilder = arrayBuilder.add(encodeAlgMap(algorithm))
        arrayBuilder = arrayBuilder.add(Map())
        arrayBuilder = arrayBuilder.add(payload)
        if (macValue == null) {
            throw CborException("macValue is null")
        }
        arrayBuilder = arrayBuilder.add(macValue)
        builder = arrayBuilder.end()

        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    fun appendToNestedStructure() : DataItem {
        val arr = Array()

        // protected header
        var protectedHeaderBuilder = CborBuilder()
        val protectedHeaderMap = protectedHeaderBuilder.addMap()
        protectedHeaderMap.put(UnsignedInteger(1.toLong()), UnsignedInteger(5.toLong()))
        protectedHeaderBuilder = protectedHeaderMap.end()
        val headerOutputStream = ByteArrayOutputStream()
        CborEncoder(headerOutputStream).encode(protectedHeaderBuilder.build())
        val protectedHeaderBytes = headerOutputStream.toByteArray()

        arr.add(ByteString(protectedHeaderBytes))

        // unprotected header
        val unprotectedHeaderMap = Map()
        arr.add(unprotectedHeaderMap)

        // payload
        //arr.add(ByteString(payload))
        arr.add(SimpleValue.NULL)

        // mac
        arr.add(ByteString(macValue))

        return arr
    }

    fun encodeAlgMap(string: String): ByteArray? {
        if (string == algorithm) {
            val outputStream = ByteArrayOutputStream()
            var builder = CborBuilder()
            var mapBuilder = builder.addMap()
            mapBuilder = mapBuilder.put(HMAC256_ALGORITHM_LABEL, UnsignedInteger(
                HMAC256_ALGORITHM_VALUE))
            builder = mapBuilder.end()
            CborEncoder(outputStream).encode(builder.build())
            return outputStream.toByteArray()
        }
        return null
    }
    class Builder : AbstractCborStructureBuilder() {
        private var payload : ByteArray? = null
        private var macValue : ByteArray? = null

        fun decode(arr : Array) = apply {
            if (arr?.dataItems?.size == 4) {
                val algorithm = arr.dataItems[0] as? ByteString
                decodeAlg(algorithm)
                validateUnprotectedHeader(arr.dataItems[1])
                payload = (arr.dataItems[2] as? ByteString)?.bytes
                macValue = (arr.dataItems[3] as? ByteString)?.bytes
            }
        }

        fun decodeEncoded(data : ByteArray) = apply {
            try {
                val stream = ByteArrayInputStream(data)
                val dataItems = CborDecoder(stream).decode()
                if (dataItems.size > 0) {
                    val structureItems = dataItems[0] as? Array
                    if (structureItems?.dataItems?.size == 4) {
                        return decode(structureItems)
                    }
                }
            } catch (ex: CborException) {
                Log.e(LOG_TAG, "Invalid COSE_Mac0")
            }
        }

        private fun validateUnprotectedHeader(dataItem: DataItem?) {
            val map = dataItem as? Map ?: throw CborException("Unprotected header is not a map")
            if (map.keys.isNotEmpty()) {
                throw CborException("Unprotected header map is not empty")
            }
        }

        private fun decodeAlg(algorithm: ByteString?) {
            try {
                val algItems = CborDecoder.decode(algorithm?.bytes)
                algItems?.let {
                    val algMap = asMap(algItems,0)
                    val algDataItem = algMap.get(HMAC256_ALGORITHM_LABEL) as? UnsignedInteger
                    val algValue = algDataItem?.value
                    algValue?.let {
                        if (algValue.toLong() != HMAC256_ALGORITHM_VALUE) {
                            throw CborException("Invalid alg")
                        }
                    }
                }
            } catch (ex: CborException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
        }

        fun setPayload(payload: ByteArray?) = apply {
            this.payload = payload
        }

        fun setMacValue(macValue: ByteArray?) = apply {
            this.macValue = macValue
        }

        fun build()  : CoseMac0 {
            return CoseMac0(payload, macValue)
        }
    }
}


