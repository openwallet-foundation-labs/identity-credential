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

import android.os.Build
import android.se.omapi.Channel
import android.se.omapi.Reader
import android.se.omapi.SEService
import android.se.omapi.Session
import androidx.annotation.RequiresApi
import com.android.identity.context.applicationContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.Arrays
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A class representing a transport mechanism for interacting with a Secure
 * Element (SE) using the OMAPI service.
 */
@RequiresApi(Build.VERSION_CODES.P)
object DirectAccessOmapiTransport {
    const val TAG: String = "DirectAccessOmapiTransport"
    private const val CONNECTION_TIMEOUT_MS: Long = 3000
    private const val ESE_READER = "eSE1"

    // IS07816.NO_ERROR = 0x9000
    private val SUCCESS: ByteArray = byteArrayOf(0x90.toByte(), 0x00)
    private var seService: SEService
    private var eseChannel: Channel? = null
    private var eseReader: Reader? = null
    private var eseSession: Session? = null

    // Use mutex to ensure accesses to vars seService, eseChannel, eseReader,
    // and eseSession are thread safe by requiring the lock for every public
    // function.
    private val mutex = Mutex()

    init {
        val executor = Executors.newSingleThreadExecutor()
        seService = SEService(applicationContext, executor) {}
    }

    /**
     * Checks if a connection to the Secure Element is currently open.
     *
     * @return `true` if the connection is open; otherwise, `false`.
     * @throws IOException
     */
    @get:Throws(IOException::class)
    private val isConnected: Boolean
        get() {
            if (eseChannel == null) {
                return false
            }
            return eseChannel!!.isOpen
        }

    /**
     * The maximum transceive length supported by the underlying Secure Element.
     *
     * This value depends on the implementation of the transport and the
     * capabilities of the Secure Element.
     *
     * @return The maximum number of bytes that can be sent in a single
     *      transceive operation.
     */
    val maxTransceiveLength: Int
        get() = // TODO: Dynamically calculate based on the SE instead of hardcoded
            // This value is set based on Pixel's eSE APDU Buffer size.
            261

    /**
     * Opens a connection to the Secure Element and selects the DirectAccess
     * Applet.
     *
     * This function establishes the necessary communication channel to the
     * Secure Element. Ensure to check the connection status before proceeding
     * with data transmission.
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun openConnection() {
        runBlocking {
            mutex.withLock {
                ensureConnected()
            }
        }
    }

    private fun ensureConnected() {
        if (!isConnected) {
            val provisionAppletAid =
                byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x02, 0x48, 0x00, 0x01, 0x01, 0x01)
            initialize(provisionAppletAid)
        }
    }

    private fun isSelectApdu(input: ByteArray): Boolean {
        // The minimum size is 6 since a select apdu is expected to have length at input[4]
        // and aid from input[5] onwards.
        if (input.size < 6) {
            return false
        }
        return (input[1] == 0xA4.toByte()) && (input[2] == 0x04.toByte())
    }

    private fun getAid(input: ByteArray): ByteArray {
        val length = input[4]
        val aid = Arrays.copyOfRange(input, 5, 5 + length)
        return aid
    }

    /**
     * Transmits data over the opened channel.
     *
     * @param input The data to be sent to the DirectAccess applet (in SE).
     * @throws IOException
     */
    @Throws(IOException::class)
    fun sendData(input: ByteArray): ByteArray {
        return runBlocking {
            mutex.withLock {
                if (isSelectApdu(input)) {
                    // Close existing channel and open basic channel again
                    reset()
                    initialize(getAid(input))
                    return@runBlocking SUCCESS
                }
                ensureConnected()

                eseChannel.let {
                    if (it == null) {
                        throw IOException("Channel is not opened.")
                    }

                    if (it.selectResponse == null ||
                        it.selectResponse!!.size < 2
                    ) {
                        throw IOException("No applet selected.")
                    }

                    val responseState = it.selectResponse!!.takeLast(2).toByteArray()
                    if (!SUCCESS.contentEquals(responseState)) {
                        throw IOException("Applet selection failed.")
                    }
                    return@runBlocking it.transmit(input)
                }
            }
        }
    }

    /**
     * Closes the currently active connection to the Secure Element.
     *
     * This function releases resources associated with the open channel.
     * It is recommended to call this function when the communication session
     * is complete.
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun closeConnection() {
        runBlocking {
            mutex.withLock {
                reset()
            }
        }
    }

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
        // In the rare case where the seService is disconnected,
        // re-initialize and wait CONNECTION_TIMEOUT_MS milliseconds
        // for the connection.
        if (!seService.isConnected) {
            val executor = Executors.newSingleThreadExecutor()
            val latch = CountDownLatch(1)
            seService = SEService(applicationContext, executor, latch::countDown)
            val connected = latch.await(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!connected || !seService.isConnected) {
                throw IllegalStateException("Failed to connect to SEService")
            }
        }

        reset()
        eseReader = seService.readers.find { ESE_READER == it.name }
        eseReader.let {
            if (it == null) throw IOException("eSE reader not available")
            if (!it.isSecureElementPresent) throw IOException("Secure Element not present")

            eseSession = it.openSession()
        }

        eseSession.let {
            if (it == null) throw IOException("Could not open session.")
            eseChannel = it.openBasicChannel(aid)
        }

        if (eseChannel == null) {
            throw IOException("Could not open channel.")
        }
    }
}
