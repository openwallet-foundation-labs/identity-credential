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

/**
 * Interface representing a transport mechanism for interacting with a Secure Element (SE).
 * The transport implementation determines how data is transmitted between the application
 * and the Secure Element. There are two supported implementations:
 *
 * 1. [DirectAccessOmapiTransport]:
 *     - Sends data using the OMAPI service
 *
 * 2. [DirectAccessSocketTransport]:
 *     - Sends data to an external application running on host.
 *     - Commonly used for testing with JCOP simulator.
 *
 * Each transport implementation provides specific handling for the initialization,
 * communication and termination of Secure Element connection.
 */
interface DirectAccessTransport {
    /**
     * Checks if a connection to the Secure Element is currently open.
     *
     * @return `true` if the connection is open; otherwise, `false`.
     * @throws IOException
     */
    @get:Throws(IOException::class)
    val isConnected: Boolean

    /**
     * The maximum transceive length supported by the underlying Secure Element.
     *
     * This value depends on the implementation of the transport and the capabilities
     * of the Secure Element.
     *
     * @return The maximum number of bytes that can be sent in a single transceive
     *         operation.
     */
    val maxTransceiveLength: Int

    /**
     * Opens a connection to the Secure Element and selects the DirectAccess Applet.
     *
     * This function establishes the necessary communication channel to the Secure
     * Element. Ensure to check the connection status before proceeding with
     * data transmission.
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun openConnection()

    /**
     * Transmits data over the opened channel.
     *
     * @param input The data to be sent to the DirectAccess applet (in SE).
     * @throws IOException
     */
    @Throws(IOException::class)
    fun sendData(input: ByteArray): ByteArray

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
    fun closeConnection()
}
