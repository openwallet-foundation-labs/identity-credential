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

import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnsignedInteger

class NfcDeviceRetrievalMethod(
    override val type: Int,
    override val version: Int,
    override val retrievalOptions: NfcOptions?
) :
    DeviceRetrievalMethod() {

    companion object {
        val MAX_COMMAND_LENGTH_KEY = 0
        val MAX_RESPONSE_LENGTH_KEY = 1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NfcDeviceRetrievalMethod

        if (type != other.type) return false
        if (version != other.version) return false
        if (retrievalOptions != other.retrievalOptions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + version
        result = 31 * result + retrievalOptions.hashCode()
        return result
    }

    class NfcOptions(
        val maxCommandLength: Int,
        val maxResponseLength: Int
    ) : RetrievalOptions() {
        override val options: kotlin.collections.Map<Int, Any>
            get() {
                // Create map with the option values
                val opt = mutableMapOf<Int, Any>()
                opt[MAX_COMMAND_LENGTH_KEY] = maxCommandLength
                opt[MAX_RESPONSE_LENGTH_KEY] = maxResponseLength

                return opt.toMap()
            }

        companion object {
            fun decode(map: Map?): NfcOptions? {
                map?.let {
                    val mCommandLenDataItem =
                        it.get(UnsignedInteger(MAX_COMMAND_LENGTH_KEY.toLong())) as? UnsignedInteger
                    val mCommandLen = mCommandLenDataItem?.value?.toInt() ?: return null
                    val mResponseLenDataItem =
                        it.get(UnsignedInteger(MAX_RESPONSE_LENGTH_KEY.toLong())) as? UnsignedInteger
                    val mResponseLen = mResponseLenDataItem?.value?.toInt() ?: return null
                    return NfcOptions(mCommandLen, mResponseLen)
                }
                return null
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NfcOptions

            if (maxCommandLength != other.maxCommandLength) return false
            if (maxResponseLength != other.maxResponseLength) return false

            return true
        }

        override fun hashCode(): Int {
            var result = maxCommandLength
            result = 31 * result + maxResponseLength
            return result
        }
    }


}
