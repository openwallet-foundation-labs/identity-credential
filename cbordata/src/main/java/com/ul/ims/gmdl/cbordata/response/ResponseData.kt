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
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import java.io.ByteArrayOutputStream
import java.io.Serializable

class ResponseData private constructor(
    val issuerSigned : IssuerSigned,
    val deviceSigned : DeviceSigned,
    val erros : Errors
) : AbstractCborStructure(), Serializable {
    companion object {
        const val KEY_ISSUER_SIGNED = "issuerSigned"
        const val KEY_DEVICE_SIGNED = "deviceSigned"
        const val KEY_ERRORS = "errors"
    }

    fun toDataItem(): Map {
        val map = Map()

        // IssuerSigned
        map.put(toDataItem(KEY_ISSUER_SIGNED), toDataItem(issuerSigned))

        // DeviceSigned
        map.put(toDataItem(KEY_DEVICE_SIGNED), toDataItem(deviceSigned))

        // Error
        erros.errors.forEach { (key, value) ->
            val itemArr = Array()
            value.forEach {
                val mapError = Map()
                mapError.put(toDataItem(it.dataItem), toDataItem(it.errorCode.toLong()))
                itemArr.add(mapError)
            }
            map.put(toDataItem(key), itemArr)
        }

        return map
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).nonCanonical().encode(toDataItem())

        return outputStream.toByteArray()
    }

    class Builder {
        private var issuerSigned : IssuerSigned? = null
        private var deviceSigned : DeviceSigned? = null

        private var errors : Errors = Errors.Builder().build()

        fun setIssuerSigned(issuerSigned : IssuerSigned) = apply {
            this.issuerSigned = issuerSigned
        }

        fun setDeviceSigned(deviceSigned : DeviceSigned?) = apply {
            deviceSigned?.let {
                this.deviceSigned = deviceSigned
            }
        }

        fun setErrors(errors : Errors) = apply {
            this.errors = errors
        }

        fun decode(map : Map) = apply {
            map.keys.forEach {item->
                val key : UnicodeString? = item as? UnicodeString
                val value = map.get(item) as? Map
                when (key?.string) {
                    KEY_ISSUER_SIGNED -> {
                        setIssuerSigned(
                            IssuerSigned.Builder()
                                .decode(value)
                                .build()
                        )
                    }
                    KEY_DEVICE_SIGNED -> {
                        setDeviceSigned(
                            DeviceSigned.Builder()
                                .decode(value)
                                .build()
                        )
                    }
                    KEY_ERRORS -> {
                        setErrors(
                            Errors.Builder()
                                .decode(value)
                                .build()
                        )
                    }
                }
            }
        }
        fun build() : ResponseData? {
            issuerSigned?.let {issuer->
                deviceSigned?.let {device->
                        return ResponseData(
                            issuer,
                            device,
                            errors
                        )

                }
            }
            return null
        }

    }
}