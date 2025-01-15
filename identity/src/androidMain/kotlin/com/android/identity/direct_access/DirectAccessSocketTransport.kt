/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.identity.direct_access

import kotlinx.io.IOException
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Socket

/**
 * For use with emulator running on desktop
 */
class DirectAccessSocketTransport(private val provisionAppletAid: ByteArray) :
    DirectAccessTransport {
    private val PORT = 8080
    private val IPADDR = "192.168.9.112"
    private val MAX_RECV_BUFFER_SIZE = 700
    private var mSocket: Socket? = null
    private var socketStatus = false

    @Throws(IOException::class)
    override fun openConnection() {
        if (!isConnected) {
            val serverAddress = InetAddress.getByName(IPADDR)
            mSocket = Socket(serverAddress, PORT)
            socketStatus = true
            // Select Provision Applet
            selectApplet(provisionAppletAid)
        }
    }

    private fun selectApplet(aid : ByteArray) : ByteArray {
        val selectCmd = ByteArray(aid.size + 5 + 1)
        selectCmd[0] = 0x00
        selectCmd[1] = 0xA4.toByte()
        selectCmd[2] = 0x04
        selectCmd[3] = 0x00
        selectCmd[4] = aid.size.toByte()
        System.arraycopy(
            aid,
            0,
            selectCmd,
            5,
            aid.size
        )
        selectCmd[selectCmd.size - 1] = 0
        return sendData(selectCmd)
    }

    @Throws(IOException::class)
    override fun sendData(inData: ByteArray): ByteArray {
        var count = 1
        while (!socketStatus && count++ < 5) {
            try {
                Thread.sleep(1000)
                println("SocketTransport Trying to open socket connection... count: $count")
                openConnection()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        if (count >= 5) {
            throw IOException("SocketTransport Failed to open socket connection")
        }

        // Prepend the input length to the inputData before sending.
        val length = ByteArray(2)
        length[0] = (inData.size shr 8 and 0xFF).toByte()
        length[1] = (inData.size and 0xFF).toByte()
        try {
            val bs = ByteArrayOutputStream()
            bs.write(length)
            bs.write(inData)
            val outputStream = mSocket!!.getOutputStream()
            outputStream.write(bs.toByteArray())
            outputStream.flush()
        } catch (e: IOException) {
            throw IOException("SocketTransport Failed to send data over socket. Error: " + e.message)
        }
        return readData()
    }

    @Throws(IOException::class)
    override fun closeConnection() {
        if (mSocket != null) {
            mSocket!!.close()
            mSocket = null
        }
        socketStatus = false
    }

    override val isConnected: Boolean
        get() = socketStatus

    override val maxTransceiveLength: Int
        get() = MAX_RECV_BUFFER_SIZE

    private fun readData(): ByteArray {
        val buffer = ByteArray(MAX_RECV_BUFFER_SIZE)
        var expectedResponseLen = 0
        var totalBytesRead = 0
        val bs = ByteArrayOutputStream()
        do {
            var offset: Short = 0
            val inputStream = mSocket!!.getInputStream()
            var numBytes = inputStream.read(buffer, 0, MAX_RECV_BUFFER_SIZE)
            if (numBytes < 0) {
                throw IOException("SocketTransport Failed to read data from socket.")
            }
            totalBytesRead += numBytes
            if (expectedResponseLen == 0) {
                expectedResponseLen = (buffer[1].toInt() and 0xFF)
                expectedResponseLen = (expectedResponseLen or (buffer[0].toInt() shl 8 and 0xFF00))
                expectedResponseLen += 2
                numBytes -= 2
                offset = 2
            }
            bs.write(buffer, offset.toInt(), numBytes)
        } while (totalBytesRead < expectedResponseLen)
        return bs.toByteArray()
    }
}
