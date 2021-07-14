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

package com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods

import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Map
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BleDeviceRetrievalMethod(
    override val type: Int,
    override val version: Int,
    override val retrievalOptions: BleOptions?
) : DeviceRetrievalMethod() {

    companion object {
        val PERIPHERAL_SERVER_KEY = 0
        val CENTRAL_CLIENT_KEY = 1
        val PERIPHERAL_SERVER_UUID_KEY = 10
        val CENTRAL_CLIENT_UUID_KEY = 11
        val PERIPHERAL_MAC_ADDRESS_KEY = 20
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BleDeviceRetrievalMethod)
            return false
        if (other.type != type)
            return false
        if (other.version != version)
            return false
        if (retrievalOptions != other.retrievalOptions)
            return false
        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + version
        result = 31 * result + (retrievalOptions?.hashCode() ?: 0)
        return result
    }

    class BleOptions(
        val peripheralServer: Boolean,
        val centralClient: Boolean,
        val peripheralServerUUID: UUID?,
        val centralClientUUID: UUID?,
        val mac: String?
    ) : RetrievalOptions() {
        override val options: kotlin.collections.Map<Int, Any>
            get() {
                // Create map with the option values
                val opt = mutableMapOf<Int, Any>()
                opt[PERIPHERAL_SERVER_KEY] = peripheralServer
                if (peripheralServer && peripheralServerUUID != null) {
                    opt[PERIPHERAL_SERVER_UUID_KEY] = peripheralServerUUID
                }
                opt[CENTRAL_CLIENT_KEY] = centralClient
                if (centralClient && centralClientUUID != null) {
                    opt[CENTRAL_CLIENT_KEY] = centralClientUUID
                }
                if (mac != null) {
                    opt[PERIPHERAL_MAC_ADDRESS_KEY] = mac
                }
                return opt.toMap()
            }

        companion object {
            fun decode(map: Map?): BleOptions? {
                map?.let { m ->
                    val perKey = UnsignedInteger(PERIPHERAL_SERVER_KEY.toLong())
                    val cenKey = UnsignedInteger(CENTRAL_CLIENT_KEY.toLong())
                    val perUKey = UnsignedInteger(PERIPHERAL_SERVER_UUID_KEY.toLong())
                    val cenUKey = UnsignedInteger(CENTRAL_CLIENT_UUID_KEY.toLong())
                    val macKey = UnsignedInteger(PERIPHERAL_MAC_ADDRESS_KEY.toLong())

                    val peripheralServer = decodeBoolean(m.get(perKey)) ?: false
                    val centralClient = decodeBoolean(m.get(cenKey)) ?: false
                    val psUuid = (m.get(perUKey) as? ByteString)
                    val ccUuid = (m.get(cenUKey) as? ByteString)
                    var peripheralUUID: UUID? = null
                    psUuid?.let {
                        peripheralUUID = uuidFromBytes(it.bytes)
                    }
                    var centralUUID: UUID? = null
                    ccUuid?.let {
                        centralUUID = uuidFromBytes(it.bytes)
                    }
                    val macString = m.get(macKey) as? UnicodeString
                    var mac: String? = null
                    macString?.let {
                        mac = macString.string
                    }

                    return BleOptions(
                        peripheralServer,
                        centralClient,
                        peripheralUUID,
                        centralUUID,
                        mac
                    )
                }
                return null
            }

            private fun uuidFromBytes(bytes: ByteArray): UUID {
                check(bytes.size == 16) { "Expected 16 bytes, found " + bytes.size }
                val data: ByteBuffer = ByteBuffer.wrap(bytes, 0, 16)
                data.order(ByteOrder.LITTLE_ENDIAN)
                return UUID(data.getLong(0), data.getLong(8))
            }

            private fun decodeBoolean(get: DataItem?): Boolean? {
                get?.let {
                    if (get.majorType != MajorType.SPECIAL) {
                        return null
                    }
                    val simpleValue = get as? SimpleValue
                    simpleValue?.let {
                        return when (simpleValue.simpleValueType) {
                            SimpleValueType.FALSE -> false
                            SimpleValueType.TRUE -> true
                            else -> null
                        }
                    }
                }
                return null
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BleOptions

            if (peripheralServer != other.peripheralServer) return false
            if (centralClient != other.centralClient) return false
            if (peripheralServerUUID != other.peripheralServerUUID) return false
            if (centralClientUUID != other.centralClientUUID) return false
            if (mac != other.mac) return false

            return true
        }

        override fun hashCode(): Int {
            var result = peripheralServer.hashCode()
            result = 31 * result + centralClient.hashCode()
            result = 31 * result + (peripheralServerUUID?.hashCode() ?: 0)
            result = 31 * result + (centralClientUUID?.hashCode() ?: 0)
            result = 31 * result + (mac?.hashCode() ?: 0)
            return result
        }
    }
}
