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

import android.util.Log
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.DeviceNameSpaces
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

class DeviceSigned private constructor(
    val deviceNameSpaces: DeviceNameSpaces,
    val deviceAuth: DeviceAuth
) : AbstractCborStructure(), Serializable {
    companion object {
        private const val LOG_TAG = "DeviceSigned"
        const val KEY_NAMESPACES = "nameSpaces"
        const val KEY_DEVICE_AUTH = "deviceAuth"
    }

    fun toDataItem(): Map {
        val map = Map()

        //NameSpaces
        val byteString = ByteString(deviceNameSpaces.encode())
        byteString.setTag(24)
        map.put(toDataItem(KEY_NAMESPACES), byteString)

        // DeviceAuth
        map.put(toDataItem(KEY_DEVICE_AUTH), toDataItem(deviceAuth))

        return map
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).nonCanonical().encode(toDataItem())

        return outputStream.toByteArray()
    }

    class Builder {
        private lateinit var deviceNameSpaces: DeviceNameSpaces
        private lateinit var deviceAuth: DeviceAuth

        fun decode(data : ByteArray) = apply {
            try {
                val stream = ByteArrayInputStream(data)
                val structureItems = CborDecoder(stream).decode()[0]
                val map : Map? = structureItems as? Map
                decode(map)
            } catch (ex : CborException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
        }

        fun decode(map: Map?) = apply {
            map?.let {
                val dNSDataItem: DataItem? = map.get(UnicodeString(KEY_NAMESPACES))
                decodeDeviceNameSpaces(dNSDataItem)
                val dAuthDataItem: DataItem? = map.get(UnicodeString(KEY_DEVICE_AUTH))
                decodeDeviceAuth(dAuthDataItem)
            }
        }

        private fun decodeDeviceAuth(dAuthDataItem: DataItem?) {
            val dAuthMap = dAuthDataItem as? Map
            deviceAuth = DeviceAuth.Builder().decode(dAuthMap).build()
        }

        private fun decodeDeviceNameSpaces(dNSDataItem: DataItem?) {
            deviceNameSpaces = DeviceNameSpaces.Builder().build()
        }

        fun setDeviceNameSpaces(deviceNameSpaces: DeviceNameSpaces) = apply {
            this.deviceNameSpaces = deviceNameSpaces
        }

        fun setDeviceAuth(deviceAuth: DeviceAuth) = apply {
            this.deviceAuth = deviceAuth
        }

        fun build() : DeviceSigned {
            return DeviceSigned(deviceNameSpaces, deviceAuth)
        }
    }
}