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
package com.android.identity.android.direct_access

import android.content.Context
import android.os.Build
import android.se.omapi.Channel
import android.se.omapi.Reader
import android.se.omapi.SEService
import android.se.omapi.Session
import androidx.annotation.RequiresApi
import com.android.identity.direct_access.DirectAccessTransport
import java.io.IOException
import java.util.Arrays
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executor
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@RequiresApi(Build.VERSION_CODES.P)
class DirectAccessOmapiTransport(
    context: Context,
    private val provisionAppletAid: ByteArray
) : DirectAccessTransport {
    private val seService: SEService
    private var eseChannel: Channel? = null
    private var eseReader: Reader? = null
    private var eseSession: Session? = null

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private var connected = false
    private val seListener = SEService.OnConnectedListener {
        lock.withLock {
            connected = true
            condition.signal()
        }
    }

    private class SynchronousExecutor : Executor {
        override fun execute(r: Runnable) {
            r.run()
        }
    }

    init {
        seService = SEService(context, SynchronousExecutor(), seListener)
    }

    private fun waitForConnection() {
        class ServiceConnectionTimerTask : TimerTask() {
            override fun run() {
                lock.withLock { condition.signalAll() }
            }
        }
        val connectionTimer = Timer()
        connectionTimer.schedule(ServiceConnectionTimerTask(), 3000)
        lock.withLock {
            if (!connected) {
                try {
                    condition.await()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            if (!connected) {
                throw TimeoutException(
                    "Service could not be connected after 3000 ms"
                )
            }
            connectionTimer.cancel()
        }
    }

    @Throws(IOException::class)
    override fun openConnection() {
        if (!isConnected) {
            initialize(provisionAppletAid)
        }
    }

    private fun isSelectApdu(input: ByteArray): Boolean {
        return (input[1] == 0xA4.toByte()) && (input[2] == 0x04.toByte())
    }

    private fun getAid(input: ByteArray): ByteArray {
        val length = input[4]
        val aid = Arrays.copyOfRange(input, 5, 5 + length)
        return aid
    }

    @Throws(IOException::class)
    override fun sendData(input: ByteArray): ByteArray {
        if (isSelectApdu(input)) {
            // Close existing channel and open basic channel again
            closeConnection()
            initialize(getAid(input))
            return byteArrayOf(0x90.toByte(), 0x00)
        } else {
            openConnection()
        }
        return transceive(input)!!
    }

    @Throws(IOException::class)
    override fun closeConnection() {
        reset()
    }

    @get:Throws(IOException::class)
    override val isConnected: Boolean
        get() {
            if (eseChannel == null) {
                return false
            }
            return eseChannel!!.isOpen
        }

    override val maxTransceiveLength: Int
        get() =// TODO
            // This value is set based on Pixel's eSE APDU Buffer size.
            261

    private fun reset() {
        if (eseChannel != null) {
            eseChannel!!.close()
            eseChannel = null
        }
        if (eseSession != null) {
            eseSession!!.close()
            eseSession = null
        }
        if (eseReader != null) {
            eseReader!!.closeSessions()
            eseReader = null
        }
    }

    @Throws(IOException::class)
    private fun initialize(aid: ByteArray) {
        reset()
        if (!seService.isConnected) {
            waitForConnection()
        }
        val readers = seService.readers
        for (reader in readers) {
            if (ESE_READER == reader.name) {
                eseReader = reader
            }
        }
        if (eseReader == null) {
            throw IOException("eSE reader not available")
        }

        if (!eseReader!!.isSecureElementPresent) {
            throw IOException("Secure Element not present")
        }

        eseSession = eseReader!!.openSession()
        if (eseSession == null) {
            throw IOException("Could not open session.")
        }
        eseChannel = eseSession!!.openBasicChannel(aid)
        if (eseChannel == null) {
            throw IOException("Could not open channel.")
        }
    }

    @Throws(IOException::class)
    private fun transceive(input: ByteArray): ByteArray? {
        val selectResponse = eseChannel!!.selectResponse
        if ((selectResponse!!.size < 2) ||
            ((selectResponse[selectResponse.size - 1].toInt() and 0xFF) != 0x00) ||
            ((selectResponse[selectResponse.size - 2].toInt() and 0xFF) != 0x90)
        ) {
            return null
        }
        return eseChannel!!.transmit(input)
    }

    companion object {
        const val TAG: String = "DirectAccessOmapiTransport"
        private const val DIRECT_ACCESS_CHANNEL: Byte = 0
        private const val ESE_READER = "eSE1"
    }
}
