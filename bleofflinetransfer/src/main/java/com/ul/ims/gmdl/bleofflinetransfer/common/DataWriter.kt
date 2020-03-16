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

package com.ul.ims.gmdl.bleofflinetransfer.common

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.ul.ims.gmdl.bleofflinetransfer.DATA_PENDING
import com.ul.ims.gmdl.bleofflinetransfer.END_OF_DATA
import com.ul.ims.gmdl.bleofflinetransfer.utils.ByteUtils
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import java.nio.ByteBuffer

class DataWriter(
        data: ByteArray,
        private val chunkSize: Int,
        private val bluetoothGatt: BluetoothGatt,
        private val holderCharacteristic: BluetoothGattCharacteristic,
        private val eventListener: BleEventListener
) {
    // Intermediate parts can be requested from the BLE callback thread so we need to keep this out of CPU cache
    @Volatile
    private var dataPart: ByteBuffer = ByteBuffer.wrap(data)

    companion object {
        const val TAG = "DataWriter"
    }

    fun writeInParts() {
        if (dataPart.remaining() > chunkSize)
            writeFirstPart()
         else
            writeLastPart()
    }

    fun setAsFirstPart() : ByteArray {
        val bytes = ByteArray(chunkSize)
        dataPart.get(bytes, 0, chunkSize)

        return byteArrayOf(DATA_PENDING) + bytes
    }

    fun setAsLastPart() : ByteArray {
        val bytes = ByteArray(dataPart.remaining())
        dataPart.get(bytes, 0, dataPart.remaining())

        return byteArrayOf(END_OF_DATA) + bytes
    }

    private fun writeFirstPart() {
        Log.d(TAG, "Writing first part of data: # of bytes left is ${dataPart.remaining()}")
        write(setAsFirstPart())
        Log.d(TAG, "New dataPart length: ${dataPart.remaining()}")
        eventListener.onBLEEvent("First dataPart written", EventType.TRANSFER_IN_PROGRESS)
    }

    private fun writeIntermediatePart() {
        Log.d(TAG, "Writing data in parts: # of bytes left is ${dataPart.remaining()}")
        write(setAsFirstPart())
        Log.d(TAG, "New dataPart length: ${dataPart.remaining()}")
        eventListener.onBLEEvent("Intermediate dataPart written", EventType.TRANSFER_IN_PROGRESS)
    }

    private fun writeLastPart() {
        if (!dataPart.hasRemaining()) {
            return
        }
        Log.d(TAG, "Writing last part of data: # of bytes left is ${dataPart.remaining()}")
        val bytes = setAsLastPart()
        write(bytes)
        Log.d(TAG, "Wrote last part of data: ${ByteUtils.bytesToHex(bytes)}")
        eventListener.onBLEEvent("Data write finished", EventType.TRANSFER_COMPLETE)
    }

    private fun write(bytes: ByteArray) {
        holderCharacteristic.value = bytes
        var writeSuccess = bluetoothGatt.writeCharacteristic(holderCharacteristic)
        Log.d(TAG, "writeCharacteristic(${ByteUtils.bytesToHex(holderCharacteristic.value)}): " +
                "data = ${ByteUtils.bytesToHex(bytes)}, Successfully written = $writeSuccess")
        while (!writeSuccess) {
            Log.d(TAG, "writeCharacteristic = $writeSuccess")
            writeSuccess = bluetoothGatt.writeCharacteristic(holderCharacteristic)
        }
    }

    fun characteristicWriteCalledBack() {
        if (dataPart.remaining() > chunkSize)
            writeIntermediatePart()
        else
            writeLastPart()
    }
}
