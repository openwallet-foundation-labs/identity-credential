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

import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger


class WiFiAwareDeviceRetrievalMethod(
    override val type: Int, override val version: Int,
    override val retrievalOptions: WifiOptions?
) : DeviceRetrievalMethod() {
    companion object {
        val PASS_PHRASE_KEY = 0
        val OPERATING_CLASS_KEY = 1
        val CHANNEL_NUMBER_KEY = 2
        val SUPPORTED_BANDS_KEY = 3
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WiFiAwareDeviceRetrievalMethod

        if (type != other.type) return false
        if (version != other.version) return false
        if (retrievalOptions != other.retrievalOptions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + version
        result = 31 * result + (retrievalOptions?.hashCode() ?: 0)
        return result
    }

    class WifiOptions(
        val passPhrase: String?,
        val operatingClass: Int?,
        val channelNumber: Int?,
        val supportedBands: ByteArray?
    ) : RetrievalOptions() {

        override val options: kotlin.collections.Map<Int, Any>
            get() {
                // Create map with the option values
                val opt = mutableMapOf<Int, Any>()
                passPhrase?.let {
                    opt[PASS_PHRASE_KEY] = it
                }
                operatingClass?.let {
                    opt[OPERATING_CLASS_KEY] = it
                }
                channelNumber?.let {
                    opt[CHANNEL_NUMBER_KEY] = it
                }
                supportedBands?.let {
                    opt[SUPPORTED_BANDS_KEY] = it
                }

                return opt.toMap()
            }

        companion object {
            fun decode(map: Map?): WifiOptions? {
                map?.let {
                    val ppDataItem =
                        it.get(UnsignedInteger(PASS_PHRASE_KEY.toLong())) as? UnicodeString
                    val pp = ppDataItem?.toString()
                    val ocDataItem =
                        it.get(UnsignedInteger(OPERATING_CLASS_KEY.toLong())) as? UnsignedInteger
                    val oc = ocDataItem?.value?.toInt()
                    val cnDataItem =
                        it.get(UnsignedInteger(CHANNEL_NUMBER_KEY.toLong())) as? UnsignedInteger
                    val cn = cnDataItem?.value?.toInt()
                    val scDataItem =
                        it.get(UnsignedInteger(CHANNEL_NUMBER_KEY.toLong())) as? ByteString
                    val sc = scDataItem?.bytes

                    // If it doesn't have any value return null
                    if (pp == null && oc == null && cn == null && sc == null) return null

                    return WifiOptions(pp, oc, cn, sc)
                }
                return null
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as WifiOptions

            if (passPhrase != other.passPhrase) return false
            if (operatingClass != other.operatingClass) return false
            if (channelNumber != other.channelNumber) return false
            if (supportedBands != null) {
                if (other.supportedBands == null) return false
                if (!supportedBands.contentEquals(other.supportedBands)) return false
            } else if (other.supportedBands != null) return false

            return true
        }


        override fun hashCode(): Int {
            var result = passPhrase?.hashCode() ?: 0
            result = 31 * result + (operatingClass ?: 0)
            result = 31 * result + (channelNumber ?: 0)
            result = 31 * result + (supportedBands?.contentHashCode() ?: 0)
            return result
        }
    }

}
