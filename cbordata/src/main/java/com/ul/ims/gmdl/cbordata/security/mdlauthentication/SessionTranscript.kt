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

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

class SessionTranscript private constructor (
    val deviceEngagement: ByteArray,
    val readerKey : ByteArray
) : AbstractCborStructure(), Serializable{

    companion object {
        private const val LOG_TAG = "SessionTranscript"
        const val STRUCTURE_TAG = 24L
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var arrayBuilder = builder.addArray()

        val de = toDataItem(deviceEngagement)
        de.tag = Tag(STRUCTURE_TAG)

        arrayBuilder = arrayBuilder.add(de)

        val rk = toDataItem(readerKey)
        rk.tag = Tag(STRUCTURE_TAG)

        arrayBuilder = arrayBuilder.add(rk)

        builder = arrayBuilder.end()
        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    fun encodeAsTaggedByteString(): ByteArray {
        val byteString = ByteString(encode())
        byteString.setTag(24)
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(byteString)
        return outputStream.toByteArray()
    }

    fun toDataItem() : DataItem? {
        val encoded = encode()

        val bais = ByteArrayInputStream(encoded)
        val decoded = CborDecoder(bais).decode()
        if (decoded.isNotEmpty()) {
            if (decoded[0].majorType == MajorType.ARRAY) {
               return decoded[0] as Array
            }
        }

        return null
    }

    class Builder {
        private var deviceEngagement : ByteArray = byteArrayOf()
        private var readerKey : ByteArray = byteArrayOf()

        fun decode(array : Array?) = apply {
            array?.let {
                if (array.dataItems.size == 2) {
                    val deStructure = array.dataItems[0]
                    decodeDeviceEngagementStructure(deStructure)
                    val rkStructure = array.dataItems[1]
                    decodeReaderKeyStructure(rkStructure)
                }
            }
        }

        fun decode(bytes : ByteArray) = apply {

        }

        private fun decodeReaderKeyStructure(rkStructure: DataItem?) {
            val outputStream = ByteArrayOutputStream()
            CborEncoder(outputStream).encode(rkStructure)
            val rkData = outputStream.toByteArray()
//            readerKey = CoseKey.Builder().decode(rkData).build()
        }

        private fun decodeDeviceEngagementStructure(deStructure: DataItem?) {
            val outputStream = ByteArrayOutputStream()
            CborEncoder(outputStream).encode(deStructure)
            val deData = outputStream.toByteArray()
//            deviceEngagement = DeviceEngagement.Builder().decode(deData).build()
        }

        fun setDeviceEngagement(deviceEngagement: ByteArray) = apply {
            this.deviceEngagement = deviceEngagement
        }

        fun setReaderKey(readerKey: ByteArray) = apply {
            this.readerKey = readerKey
        }

        fun build() : SessionTranscript {
          return SessionTranscript(deviceEngagement, readerKey)
        }
    }

    override fun equals(other: Any?): Boolean {
        other?.let {
            if (other is SessionTranscript) {
                val otherDE = other.deviceEngagement
                val otherRK = other.readerKey
                otherDE.let {
                    if (otherDE.contentEquals(deviceEngagement)) {
                        otherRK.let {
                            if (otherRK.contentEquals(readerKey)) {
                                return super.equals(other)
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    override fun hashCode(): Int {
        var result = deviceEngagement.hashCode()
        result = 31 * result + (readerKey.hashCode())

        return result
    }

}