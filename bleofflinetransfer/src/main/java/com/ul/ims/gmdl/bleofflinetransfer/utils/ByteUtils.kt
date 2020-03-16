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

package com.ul.ims.gmdl.bleofflinetransfer.utils

import android.bluetooth.BluetoothDevice
import com.ul.ims.gmdl.bleofflinetransfer.DATA_PENDING

/**
 * Provides utility methods for working with bytes,
 */
object ByteUtils {
    fun bytesToHex(bytes: ByteArray?): String {
        var sb = StringBuilder()
        bytes?.let {
            sb = StringBuilder(bytes.size * 2)

            for (b in bytes) {
                sb.append(String.format("%02x", b))
            }
        }
        return sb.toString()
    }

    fun deviceString(device: BluetoothDevice): String {
        return "device address: " + device.address + " with name: " + device.name
    }

    fun decodeData(byteArray: ByteArray) : ByteArray {
        return byteArray.copyOfRange(1, byteArray.size)
    }

    fun isDataPending(value: ByteArray): Boolean {
        return value[0] == DATA_PENDING
    }
}
