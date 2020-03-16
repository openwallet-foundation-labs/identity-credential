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

import co.nstant.`in`.cbor.model.UnsignedInteger
import java.util.*

class BleTransferMethod(
    override val type: Int,
    override val version: Int,
    val bleIdentification: BleIdentification?
) : ITransferMethod {

    companion object {
        val PERIPHERAL_SERVER_KEY = UnsignedInteger(0)
        val CENTRAL_CLIENT_KEY = UnsignedInteger(1)
        val PERIPHERAL_UUID_KEY = UnsignedInteger(10)
        val PERIPHERAL_MAC_ADDRESS_KEY = UnsignedInteger(11)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BleTransferMethod)
            return false
        if (other.type != type)
            return false
        if (other.version != version)
            return false
        if (bleIdentification != other.bleIdentification)
            return false
        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + version
        result = 31 * result + (bleIdentification?.hashCode() ?: 0)
        return result
    }

    class BleIdentification(
        val peripheralServer: Boolean?,
        val centralClient: Boolean?,
        val peripheralServerUUID: UUID?,
        val mac: String?
    ) {

        override fun equals(other: Any?): Boolean {
            if (other !is BleIdentification)
                return false
            if (other.peripheralServer != peripheralServer)
                return false
            if (other.centralClient != centralClient)
                return false
            if (other.peripheralServerUUID != peripheralServerUUID)
                return false
            if (other.mac != mac)
                return false
            return true
        }

        override fun hashCode(): Int {
            var result = peripheralServer?.hashCode() ?: 0
            result = 31 * result + (centralClient?.hashCode() ?: 0)
            result = 31 * result + (peripheralServerUUID?.hashCode() ?: 0)
            result = 31 * result + (mac?.hashCode() ?: 0)
            return result
        }
    }
}
