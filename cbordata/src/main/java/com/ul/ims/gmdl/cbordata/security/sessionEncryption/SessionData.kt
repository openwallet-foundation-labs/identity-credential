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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

class SessionData private constructor(val encryptedData: ByteArray?, val errorCode: Int?) :
    AbstractCborStructure(), Serializable {

    companion object {
        const val LOG_TAG = "SessionData"
        val encryptedDataLabel = UnicodeString("data")
        val errorLabel = UnicodeString("error")
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var structureMap = builder.addMap()

        // EncryptedData
        if (encryptedData != null) {
            structureMap = structureMap.put(encryptedDataLabel, toDataItem(encryptedData))
        }
        // ErrorCode
        errorCode?.let {
            structureMap = structureMap.put(errorLabel, toDataItem(it))
        }

        builder = structureMap.end()
        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    class Builder {
        private var encryptedData: ByteArray? = null
        private var errorCode: Int? = null
        private var exceptionDescription = "No exception message received"

        fun decode(data: ByteArray) = apply {
            try {
                val bais = ByteArrayInputStream(data)
                val decoded = CborDecoder(bais).decode()
                if (decoded.size == 1) {
                    val structureItems: Map? = decoded[0] as? Map
                    structureItems?.let {
                        this.encryptedData =
                            decodeEncryptedData(structureItems.get(encryptedDataLabel))
                        this.errorCode = decodeErrorCode(structureItems.get(errorLabel))
                    }
                }
            } catch (ex: CborException) {
                val message = ex.message
                if (message != null) {
                    exceptionDescription = message
                }
                Log.e(LOG_TAG, exceptionDescription)            }
        }

        fun setEncryptedData(data : ByteArray) = apply {
            encryptedData = data
        }

        fun setErrorCode(code: Int) = apply {
            errorCode = code
        }

        private fun decodeErrorCode(dataItem: DataItem?): Int? {
            if (dataItem?.majorType == MajorType.UNSIGNED_INTEGER) {
                return (dataItem as UnsignedInteger).value.toInt()
            }
            return null
        }

        private fun decodeEncryptedData(dataItem: DataItem?): ByteArray? {
            if (dataItem?.majorType == MajorType.BYTE_STRING) {
                return (dataItem as ByteString).bytes
            }
            return null
        }

        fun build() = SessionData(encryptedData, errorCode)
    }

}