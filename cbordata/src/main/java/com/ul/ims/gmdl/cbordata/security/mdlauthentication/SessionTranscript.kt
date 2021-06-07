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
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.MajorType
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable


class SessionTranscript private constructor(
    val deviceEngagementBytes: ByteArray,
    val eReaderKeyBytes: ByteArray,
    val handover: Handover
) : AbstractCborStructure(), Serializable {

    companion object {
        private const val LOG_TAG = "SessionTranscript"
        const val STRUCTURE_TAG = 24
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()

        val builder = CborBuilder().addArray()
            .add(encodeAsTagged(deviceEngagementBytes))
            .add(encodeAsTagged(eReaderKeyBytes))
            .add(handover.toDataItem()).end()

        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    private fun encodeAsTagged(byteArray: ByteArray): DataItem {
        val byteString = ByteString(byteArray)
        byteString.setTag(STRUCTURE_TAG)
        return byteString
    }

    fun encodeAsTaggedByteString(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(encodeAsTagged(encode()))
        return outputStream.toByteArray()
    }

    fun toDataItem(): DataItem? {
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
        private var deviceEngagementBytes: ByteArray = byteArrayOf()
        private var eReaderKeyBytes: ByteArray = byteArrayOf()
        private var handover: Handover? = null

        fun decode(array: Array?) = apply {
            array?.let {
                if (array.dataItems.size == 3) {
                    deviceEngagementBytes = toByteArray(array.dataItems[0])
                    eReaderKeyBytes = toByteArray(array.dataItems[1])
                    handover = Handover.Builder().decode(array.dataItems[2]).build()
                }
            }
        }

        private fun toByteArray(dataItem: DataItem?): ByteArray {
            val outputStream = ByteArrayOutputStream()
            CborEncoder(outputStream).encode(dataItem)
            return outputStream.toByteArray()
        }

        fun setDeviceEngagement(deviceEngagementBytes: ByteArray) = apply {
            this.deviceEngagementBytes = deviceEngagementBytes
        }

        fun setReaderKey(eReaderKeyBytes: ByteArray) = apply {
            this.eReaderKeyBytes = eReaderKeyBytes
        }

        fun setHandover(handover: Handover) = apply {
            this.handover = handover
        }

        fun build() = SessionTranscript(
            deviceEngagementBytes,
            eReaderKeyBytes,
            handover ?: Handover.Builder().build()
        )

    }

}