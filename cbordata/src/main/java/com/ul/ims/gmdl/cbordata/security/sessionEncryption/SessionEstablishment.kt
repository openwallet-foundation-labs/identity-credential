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

package com.ul.ims.gmdl.cbordata.security.sessionEncryption

import android.util.Log
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.security.CoseKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

class SessionEstablishment private  constructor(
    val readerKey: CoseKey?,
    val encryptedData: ByteArray?
) : AbstractCborStructure(), Serializable{

    companion object {
        const val LOG_TAG = "SessionEstablishment"
        val readerKeyLabel = UnicodeString("eReaderKey")
        val dataLabel = UnicodeString("data")
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var structureMap = builder.addMap()

        // eReaderKey
        if (readerKey != null) {
            val byteString = ByteString(readerKey.encode())
            byteString.setTag(24)
            structureMap = structureMap.put(readerKeyLabel, byteString)
        }
        // Encrypted Data
        if (encryptedData != null) {
            structureMap = structureMap.put(dataLabel, toDataItem(encryptedData))
        }

        builder = structureMap.end()
        // TODO: Use nonCanonical when cbor-java 0.9 is out.
        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    class Builder {
        private var exceptionDescription = "No exception message received"
        private var readerKey : CoseKey? = null
        private var encryptedData : ByteArray? = null

        fun decode(data: ByteArray) = apply {
            try {
                val bais = ByteArrayInputStream(data)
                val decoded = CborDecoder(bais).decode()
                if (decoded.size == 1) {
                    val structureItems : Map? = decoded[0] as? Map
                    structureItems?.let {
                        readerKey = decodeReaderKey(structureItems.get(readerKeyLabel))
                        encryptedData = decodeEncryptedData(structureItems.get(dataLabel))
                    }
                }
            } catch (ex: CborException) {
                val message = ex.message
                if (message != null) {
                    exceptionDescription = message
                }
                Log.e(LOG_TAG, exceptionDescription)
            }
        }

        fun setReaderKey(key: CoseKey?) = apply {
            readerKey = key
        }

        fun setEncryptedData(data : ByteArray) = apply {
            encryptedData = data
        }

        private fun decodeEncryptedData(dataItem : DataItem?): ByteArray? {
            if (dataItem?.majorType == MajorType.BYTE_STRING) {
                return (dataItem as ByteString).bytes
            }
            return null
        }

        private fun decodeReaderKey(dataItem: DataItem?): CoseKey? {
            if (dataItem?.majorType == MajorType.BYTE_STRING && dataItem.tag == Tag(24)) {
                return CoseKey.Builder().decode((dataItem as ByteString).bytes).build()
            }
            return null
        }

        fun build() = SessionEstablishment(readerKey, encryptedData)
    }
}