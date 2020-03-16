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

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.util.Log
import com.ul.ims.gmdl.bleofflinetransfer.DATA_PENDING
import com.ul.ims.gmdl.bleofflinetransfer.END_OF_DATA
import com.ul.ims.gmdl.bleofflinetransfer.utils.ByteUtils
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import java.nio.ByteBuffer

class DataNotifier(
    data: ByteArray,
    private val chunkSize: Int,
    private val bluetoothGattServer: BluetoothGattServer,
    private val serverToClientCharacteristic: BluetoothGattCharacteristic,
    private val eventListener: BleEventListener,
    private val currentDevice: BluetoothDevice?) {

    @Volatile
    private var dataPart: ByteBuffer = ByteBuffer.wrap(data)

    fun notifyInParts() {
        if (dataPart.remaining() > chunkSize)
            notifyFirstPart()
        else
            notifyLastPart()
    }

    private fun notifyFirstPart() {
        Log.i(javaClass.simpleName, "Writing first part of data: # of bytes left is ${dataPart.remaining()}")
        val bytes = setAsFirstPart()
        notifyCharacteristic(bytes)
        Log.i(javaClass.simpleName, "New dataPart: ${ByteUtils.bytesToHex(bytes)}")
        eventListener.onBLEEvent("First dataPart written", EventType.TRANSFER_IN_PROGRESS)
    }

    private fun notifyIntermediatePart() {
        Log.i(javaClass.simpleName, "Writing data in parts: # of bytes left is ${dataPart.remaining()}")
        val bytes = setAsFirstPart()
        notifyCharacteristic(bytes)
        Log.i(javaClass.simpleName, "New dataPart: ${ByteUtils.bytesToHex(bytes)}")
        eventListener.onBLEEvent("Intermediate dataPart written", EventType.TRANSFER_IN_PROGRESS)
    }

    private fun notifyLastPart() {
        if (!dataPart.hasRemaining()) {
            return
        }
        Log.i(javaClass.simpleName, "Writing last part of data: # of bytes left is ${dataPart.remaining()}")
        val bytes = setAsLastPart()
        notifyCharacteristic(bytes)
        Log.i(javaClass.simpleName, "Wrote last part of data: ${ByteUtils.bytesToHex(bytes)}")
        eventListener.onBLEEvent("Data write finished", EventType.TRANSFER_COMPLETE)
        dataPart = ByteBuffer.wrap(byteArrayOf())
    }

    private fun notifyCharacteristic(bytes: ByteArray) {
        serverToClientCharacteristic.value = bytes
        var notifySuccess: Boolean = bluetoothGattServer.notifyCharacteristicChanged(currentDevice,
                serverToClientCharacteristic, false)
        Log.i(javaClass.simpleName, "serverToClientCharacteristic value changed to ${ByteUtils.bytesToHex(bytes)} successfully: $notifySuccess")
        while (!notifySuccess) {
            Log.d(javaClass.simpleName, "notificationSent = $notifySuccess")
            notifySuccess = bluetoothGattServer.notifyCharacteristicChanged(currentDevice,
                    serverToClientCharacteristic, false)
        }
    }

    fun notificationSentCalledBack() {
        if (dataPart.remaining() > chunkSize)
            notifyIntermediatePart()
        else
            notifyLastPart()
    }

    private fun setAsFirstPart() : ByteArray {
        val bytes = ByteArray(chunkSize)
        dataPart.get(bytes, 0, chunkSize)

        return byteArrayOf(DATA_PENDING) + bytes
    }

    private fun setAsLastPart() : ByteArray {
        val bytes = ByteArray(dataPart.remaining())
        dataPart.get(bytes, 0, dataPart.remaining())

        return byteArrayOf(END_OF_DATA) + bytes
    }
}
