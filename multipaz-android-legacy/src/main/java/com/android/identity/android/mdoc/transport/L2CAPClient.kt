/*
 * Copyright 2022 The Android Open Source Project
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
package com.android.identity.android.mdoc.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import org.multipaz.util.Logger
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.buildByteString
import org.multipaz.util.appendUInt32
import org.multipaz.util.getUInt32
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.TimeUnit

internal class L2CAPClient(private val context: Context, val listener: Listener) {
    
    private var socket: BluetoothSocket? = null
    private val writerQueue: BlockingQueue<ByteArray> = LinkedTransferQueue()
    var writingThread: Thread? = null
    private var inhibitCallbacks = false

    @RequiresApi(api = Build.VERSION_CODES.Q)
    fun connect(bluetoothDevice: BluetoothDevice, psm: Int) {

        // As per https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#cancelDiscovery()
        // we should cancel any ongoing discovery since it interfere with the connection process
        val adapter = context.getSystemService(
            BluetoothManager::class.java
        ).adapter
        if (!adapter.cancelDiscovery()) {
            reportError(Error("Error canceling BluetoothDiscovery"))
            return
        }
        Logger.d(TAG, "Connecting to " + bluetoothDevice.address + " and PSM " + psm)
        val connectThread: Thread = object : Thread() {
            override fun run() {
                try {
                    socket = bluetoothDevice.createInsecureL2capChannel(psm)
                    socket!!.connect()
                } catch (e: IOException) {
                    Logger.e(TAG, "Error connecting to L2CAP socket: " + e.message, e)
                    reportError(Error("Error connecting to L2CAP socket: " + e.message, e))
                    socket = null
                    return
                }

                // Start writing thread
                writingThread = Thread { writeToSocket() }
                writingThread!!.start()

                // Let the app know we're connected.
                reportPeerConnected()

                // Reuse this thread for reading
                readFromSocket()
            }
        }
        connectThread.start()
    }

    fun disconnect() {
        inhibitCallbacks = true
        if (writingThread != null) {
            // This instructs the writing thread to shut down. Once all pending writes
            // are done, [mSocket] is closed there.
            writerQueue.add(ByteArray(0))
            writingThread = null
        }
    }

    fun writeToSocket() {
        val os = socket!!.outputStream
        try {
            while (true) {
                var messageToSend: ByteArray?
                try {
                    messageToSend = writerQueue.poll(1000, TimeUnit.MILLISECONDS)
                    if (messageToSend == null) {
                        continue
                    }
                    if (messageToSend.size == 0) {
                        Logger.d(TAG, "Empty message, exiting writer thread")
                        break
                    }
                } catch (e: InterruptedException) {
                    continue
                }
                os.write(messageToSend)
            }
        } catch (e: IOException) {
            Logger.e(TAG, "Error using L2CAP socket", e)
            reportError(Error("Error using L2CAP socket", e))
        }
        try {
            // TODO: This is to work around a bug in L2CAP
            Thread.sleep(1000)
            socket!!.close()
        } catch (e: IOException) {
            Logger.e(TAG, "Error closing socket", e)
        } catch (e: InterruptedException) {
            Logger.e(TAG, "Error closing socket", e)
        }
    }

    private fun readFromSocket() {
        Logger.d(TAG, "Start reading socket input")

        // Keep listening to the InputStream until an exception occurs.
        val inputStream = try {
            socket!!.inputStream
        } catch (e: IOException) {
            reportError(Error("Error on listening input stream from socket L2CAP", e))
            return
        }
        while (true) {
            try {
                val length = try {
                    inputStream.readNOctets(4U).getUInt32(0)
                } catch (e: Throwable) {
                    reportPeerDisconnected()
                    break
                }
                val message = inputStream.readNOctets(length)
                reportMessageReceived(message)
            } catch (e: IOException) {
                reportError(Error("Error on listening input stream from socket L2CAP", e))
                break
            }
        }
    }

    fun sendMessage(data: ByteArray) {
        writerQueue.add(buildByteString { appendUInt32(data.size).append(data) }.toByteArray())
    }

    fun reportPeerConnected() {
        if (!inhibitCallbacks) {
            listener.onPeerConnected()
        }
    }

    fun reportPeerDisconnected() {
        if (!inhibitCallbacks) {
            listener.onPeerDisconnected()
        }
    }

    fun reportMessageReceived(data: ByteArray) {
        if (!inhibitCallbacks) {
            listener.onMessageReceived(data)
        }
    }

    fun reportError(error: Throwable) {
        if (!inhibitCallbacks) {
            listener.onError(error)
        }
    }

    internal interface Listener {
        fun onPeerConnected()
        fun onPeerDisconnected()
        fun onMessageReceived(data: ByteArray)
        fun onError(error: Throwable)
    }

    companion object {
        private const val TAG = "L2CAPClient"
    }
}

// Cannot call it readNBytes() b/c that's taken on API >= 33
//
internal fun InputStream.readNOctets(len: UInt): ByteArray {
    val bsb = ByteStringBuilder()
    var remaining = len
    while (remaining > 0U) {
        val buf = ByteArray(remaining.toInt())
        val numBytesRead = this.read(buf, 0, remaining.toInt())
        if (numBytesRead == -1) {
            throw IllegalStateException("Failed reading from input stream")
        }
        bsb.append(buf)
        remaining -= numBytesRead.toUInt()
    }
    return bsb.toByteString().toByteArray()
}

