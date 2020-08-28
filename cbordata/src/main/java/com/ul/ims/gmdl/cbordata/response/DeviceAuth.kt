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
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.CoseMac0
import java.io.ByteArrayOutputStream
import java.io.Serializable

class DeviceAuth private constructor(
    val deviceSignature: CoseSign1?,
    val deviceMac: CoseMac0?
) : AbstractCborStructure(), Serializable {
    companion object {
        private const val LOG_TAG = "DeviceAuth"
        const val KEY_DEVICE_SIGNATURE = "deviceSignature"
        const val KEY_DEVICE_MAC = "deviceMac"
    }

    fun toDataItem(): Map {
        val map = Map()

        deviceSignature?.let {
            map.put(toDataItem(KEY_DEVICE_SIGNATURE), toDataItem(it))
        }

        deviceMac?.let {
            map.put(toDataItem(KEY_DEVICE_MAC), toDataItem(it))
        }

        return map
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(toDataItem())

        return outputStream.toByteArray()
    }

    class Builder {
        private var deviceSignature: CoseSign1? = null
        private var deviceMac: CoseMac0? = null

        fun decode(map : Map?) = apply {
            map?.let {
                val cS1DataItem = it.get(UnicodeString(KEY_DEVICE_SIGNATURE))
                cS1DataItem?.let {
                    decodeCoseSign1(cS1DataItem)
                }
                val cM0DataItem = it.get(UnicodeString(KEY_DEVICE_MAC))
                cM0DataItem?.let {
                    decodeCoseMac0(cM0DataItem)
                }
            }
        }

        private fun decodeCoseMac0(cM0DataItem: DataItem?) {
            if (cM0DataItem?.majorType == MajorType.ARRAY) {
                val arr = cM0DataItem as Array
                deviceMac = CoseMac0.Builder().decode(arr).build()
            }
        }

        private fun decodeCoseSign1(cS1DataItem: DataItem?) {
            if (cS1DataItem?.majorType == MajorType.ARRAY) {
                val arr = cS1DataItem as Array
                deviceSignature = CoseSign1.Builder().decode(arr).build()
            }
        }

        fun setCoseSign1(coseSign1: CoseSign1) = apply {
            this.deviceSignature = coseSign1
        }

        fun setCoseMac0(coseMac0: CoseMac0) = apply {
            this.deviceMac = coseMac0
        }

        fun build(): DeviceAuth {
            return DeviceAuth(deviceSignature, deviceMac)
        }
    }
}