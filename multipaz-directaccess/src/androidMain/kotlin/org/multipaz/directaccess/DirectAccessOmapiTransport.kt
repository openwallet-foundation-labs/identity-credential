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
package org.multipaz.directaccess

import android.os.Build
import android.se.omapi.Channel
import android.se.omapi.Reader
import android.se.omapi.SEService
import android.se.omapi.Session
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.multipaz.context.applicationContext
import java.io.IOException
import java.util.Arrays
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    private var seService: SEService?
    private var eseChannel: Channel? = null
    private var eseReader: Reader? = null
    private var eseSession: Session? = null

    // Use mutex to ensure accesses to vars seService, eseChannel, eseReader,
    // and eseSession are thread safe by requiring the lock for every public
    // function.
    private val mutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val omapiDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        seService = SEService(applicationContext, omapiDispatcher.asExecutor()) {}
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
    suspend fun openConnection() {
        mutex.withLock {
            withContext(omapiDispatcher) {
                ensureConnected()
            }
        }
    }

    private suspend fun ensureConnected() { // Make this a suspend function
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
    suspend fun sendData(input: ByteArray): ByteArray {
        return mutex.withLock {
            withContext(omapiDispatcher) {
                if (isSelectApdu(input)) {
                    // Close existing channel and open basic channel again
                    reset()
                    initialize(getAid(input))
                    return@withContext SUCCESS
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
                    return@withContext it.transmit(input)
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
    suspend fun closeConnection() {
        mutex.withLock {
            withContext(omapiDispatcher) {
                reset()
            }
        }
    }

    private fun reset() {
        if (eseChannel?.isOpen == true) { // Check if open before trying to close
            try { eseChannel?.close() } catch (e: Exception) { /* Log or ignore */ }
        }
        eseChannel = null

        // Sessions are closed by closing the reader, or explicitly if needed.
        // Reader.closeSessions() closes all sessions opened by this reader instance.
        if (eseSession?.isClosed == false) { // Check if open before trying to close
            try { eseSession?.close() } catch (e: Exception) { /* Log or ignore */ }
        }
        eseSession = null


        // Note: SEService itself doesn't have a "close" method for the service connection.
        // It's managed by the system. Readers are obtained from a connected SEService.
        // Closing readers and sessions is the primary cleanup.
        // If you re-instantiate SEService, the old instance (if any) should be garbage collected
        // if it's no longer referenced and its listener is cleared.
        // The `seService = null` in case of connection failure in `initialize` helps here.
        if (eseReader != null) {
            try { eseReader?.closeSessions() } catch (e: Exception) { /* Log or ignore */ }
            // Readers themselves don't have a close method in the standard OMAPI
            // but closeSessions() is the cleanup related to them.
        }
        eseReader = null
    }

    @Throws(IOException::class)
    private suspend fun initialize(aid: ByteArray) {
        // In the rare case where the seService is disconnected,
        // re-initialize and wait CONNECTION_TIMEOUT_MS milliseconds
        // for the connection.
        if (seService == null || !seService!!.isConnected) {
            val connectedSuccessfully = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    val listener = SEService.OnConnectedListener {
                        // Called on the omapiDispatcher's thread
                        if (continuation.isActive) {
                            if (seService?.isConnected == true) {
                                continuation.resume(true)
                            } else {
                                // This case might not happen if onConnected is only called on success,
                                // but good to be defensive.
                                continuation.resume(false)
                            }
                        }
                    }

                    // Attempt to create and connect to the SEService
                    // The SEService constructor can throw exceptions if parameters are wrong,
                    // or if the service is unavailable immediately in some rare cases.
                    try {
                        seService = SEService(applicationContext, omapiDispatcher.asExecutor(), listener)

                        // If SEService constructor itself determines it's immediately connected
                        // (some implementations might), or if there's an immediate failure
                        // that doesn't go through the listener.
                        // However, the primary path is via the listener.
                        // The SEService documentation is key here. Typically, connection is asynchronous.

                        // Cancellation handler for the coroutine
                        continuation.invokeOnCancellation {
                            // If the coroutine is cancelled (e.g., due to timeout),
                            // we might want to attempt to shutdown the SEService if it was created.
                            // However, SEService doesn't have an explicit disconnect or close for an
                            // in-progress connection attempt before onConnected.
                            // If seService instance was created, it will be handled by reset() later
                            // if connection ultimately fails or is timed out.
                        }
                    } catch (e: Exception) {
                        // If SEService constructor throws an exception
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Failed to instantiate SEService", e)
                            )
                        }
                    }
                }
            }

            if (connectedSuccessfully != true || seService == null || seService?.isConnected == false) {
                seService = null // Ensure it's null if connection failed
                throw IllegalStateException("Failed to connect to SEService within ${CONNECTION_TIMEOUT_MS}ms or service not available.")
            }
        }

        reset()
        eseReader = seService!!.readers.find { ESE_READER == it.name }
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
