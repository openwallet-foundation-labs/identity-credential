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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Build
import androidx.annotation.RequiresApi
import com.android.identity.android.util.HelperListener
import com.android.identity.android.util.launchIfAllowed
import com.android.identity.cbor.Cbor
import com.android.identity.util.Logger
import kotlinx.coroutines.CoroutineScope
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.OptionalInt
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.TimeUnit

internal class L2CAPServer(val listener: Listener, private val scope: CoroutineScope?) {

    var inhibitCallbacks = false
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private val writerQueue: BlockingQueue<ByteArray> = LinkedTransferQueue()
    private var writingThread: Thread? = null

    @RequiresApi(api = Build.VERSION_CODES.Q)
    fun start(bluetoothAdapter: BluetoothAdapter): OptionalInt {
        return try {
            // Using insecure L2CAP allows the app to use L2CAP frictionless, otherwise
            // Android will require bluetooth pairing with other device showing the pairing code
            serverSocket = bluetoothAdapter.listenUsingInsecureL2capChannel()
            val psm = serverSocket!!.psm
            val waitingForConnectionThread = Thread { waitForConnectionThread() }
            waitingForConnectionThread.start()
            OptionalInt.of(psm)
        } catch (e: IOException) {
            Logger.w(TAG, "Error creating L2CAP channel " + e.message)
            serverSocket = null
            OptionalInt.empty()
        }
    }

    fun stop() {
        inhibitCallbacks = true
        if (writingThread != null) {
            // This instructs the writing thread to shut down. Once all pending writes
            // are done, [mSocket] is closed there.
            writingThread = null
        }
        try {
            if (serverSocket != null) {
                serverSocket!!.close()
                serverSocket = null
            }
        } catch (e: IOException) {
            // Ignoring this error
            Logger.e(TAG, "Error closing server socket connection " + e.message, e)
        }
    }

    private fun waitForConnectionThread() {
        try {
            socket = serverSocket!!.accept()
            // Stop accepting new connections
            serverSocket!!.close()
            serverSocket = null
        } catch (e: IOException) {
            Logger.e(TAG, "Error getting connection from socket server for L2CAP " + e.message, e)
            reportError(Error("Error getting connection from socket server for L2CAP", e))
            return
        }

        // Start writing thread
        writingThread = Thread { writeToSocket() }
        writingThread!!.start()

        // Let the app know we have a connection
        reportPeerConnected()

        // Reuse this thread for reading
        readFromSocket()
    }

    private fun writeToSocket() {
        val os = socket!!.outputStream
        try {
            while (true) {
                var messageToSend: ByteArray?
                try {
                    messageToSend = writerQueue.poll(1000, TimeUnit.MILLISECONDS)
                    if (messageToSend == null) {
                        continue
                    }
                    if (messageToSend.isEmpty()) {
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
            // TODO: This is to work faqround a bug in L2CAP
            Thread.sleep(1000)
            socket!!.close()
        } catch (e: Exception) {
            // could be IOException, InterruptedException
            Logger.e(TAG, "Error closing socket", e)
        }
    }

    private fun readFromSocket() {
        Logger.d(TAG, "Start reading socket input")
        val pendingDataBaos = ByteArrayOutputStream()

        // Keep listening to the InputStream until an exception occurs.
        val inputStream = try {
            socket!!.inputStream
        } catch (e: IOException) {
            reportError(Error("Error on listening input stream from socket L2CAP", e))
            return
        }
        while (true) {
            val buf = ByteArray(DataTransportBle.L2CAP_BUF_SIZE)
            try {
                val numBytesRead = inputStream.read(buf)
                if (numBytesRead == -1) {
                    Logger.d(TAG, "End of stream reading from socket")
                    reportPeerDisconnected()
                    break
                }
                pendingDataBaos.write(buf, 0, numBytesRead)
                try {
                    val pendingData = pendingDataBaos.toByteArray()
                    val (endOffset, _) = Cbor.decode(pendingData, 0)
                    val dataItemBytes = pendingData.sliceArray(IntRange(0, endOffset - 1))
                    pendingDataBaos.reset()
                    pendingDataBaos.write(pendingData, endOffset, pendingData.size - endOffset)
                    reportMessageReceived(dataItemBytes)
                } catch (e: Exception) {
                    /* not enough data to decode item, do nothing */
                }
            } catch (e: IOException) {
                reportError(Error("Error on listening input stream from socket L2CAP", e))
                break
            }
        }
    }

    fun sendMessage(data: ByteArray) {
        writerQueue.add(data)
    }

    private fun reportPeerConnected() =
        listener.executeIfAllowed(inhibitCallbacks) { onPeerConnected() }

    private fun reportPeerDisconnected() =
        listener.executeIfAllowed(inhibitCallbacks) { onPeerDisconnected() }

    private fun reportMessageReceived(data: ByteArray) =
        listener.executeIfAllowed(inhibitCallbacks) { onMessageReceived(data) }

    private fun reportError(error: Throwable) =
        listener.executeIfAllowed(inhibitCallbacks) { onError(error) }

    internal interface Listener : HelperListener {
        fun onPeerConnected()
        fun onPeerDisconnected()
        fun onMessageReceived(data: ByteArray)
        fun onError(error: Throwable)
    }

    companion object {
        private const val TAG = "L2CAPServer"
    }

    /**
     * Private extension function localized to [L2CAPServer] that wraps around the extension function
     * [CoroutineScope?.launchIfAllowed] to simplify and prettify listener function callbacks.
     *
     * For ex, run a coroutine to call "onMessageReceived()" on the Listener instance
     * scope.launchIfAllowed(inhibitCallbacks, listener) { onMessageReceived() }
     *
     * can be simplified to something easier to follow
     * listener.executeIfAllowed(inhibitCallbacks) { onMessageReceived() }
     *
     * @param inhibitCallbacks whether to prevent the callback from being executed/called
     * @param callback the block of code using Listener as the function type receiver so
     * function calls are made on "this" Listener instance directly.
     */
    private fun Listener?.executeIfAllowed(
        inhibitCallbacks: Boolean,
        callback: Listener.() -> Unit
    ) {
        scope.launchIfAllowed(
            inhibitCallbacks = inhibitCallbacks,
            listener = this,
            callback = callback
        )
    }
}