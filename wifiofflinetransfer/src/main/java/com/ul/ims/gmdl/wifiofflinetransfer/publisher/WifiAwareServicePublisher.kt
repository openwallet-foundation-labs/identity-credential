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

package com.ul.ims.gmdl.wifiofflinetransfer.publisher

import android.annotation.TargetApi
import android.net.*
import android.net.wifi.aware.*
import android.util.Log
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import com.ul.ims.gmdl.wifiofflinetransfer.WifiTransportManager
import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.HttpRequest
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.entity.ContentLengthStrategy
import org.apache.http.impl.entity.StrictContentLengthStrategy
import org.apache.http.impl.io.*
import org.bouncycastle.util.encoders.Hex
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.*

@TargetApi(29)
class WifiAwareServicePublisher internal constructor(
    serviceName: String,
    private val passphrase: String,
    private val connectivityManager: ConnectivityManager,
    private val wifiTransportManager: WifiTransportManager
) : ITransportLayer, DiscoverySessionCallback() {

    companion object {
        private const val LOG_TAG = "WifiAwareServicePublisher"
    }

    private val config: PublishConfig = PublishConfig.Builder()
        .setServiceName(serviceName)
        .build()

    // Listener to notify the executor layer that we've got data
    private var executorEventListener: IExecutorEventListener? = null

    private var peerHandle: PeerHandle? = null

    private var publishSession: PublishDiscoverySession? = null

    private var socket: Socket? = null

    private var callback: ConnectivityManager.NetworkCallback? = null

    private var wifiAwareSession: WifiAwareSession? = null

    private fun setup() {

        // https://developer.android.com/guide/topics/connectivity/wifi-aware.html#create_a_connection

        // step 2 for the subscriber

        if (peerHandle == null || publishSession == null) {
            Log.d(LOG_TAG, "peer or publish session is null ")
            return
        }

        // step 3
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        Log.d(LOG_TAG, "Setting up Server Socket with " + serverSocket.inetAddress + ":" + port)

        // step 4
        var networkSpecifier : NetworkSpecifier? = null
        publishSession?.let { session ->
            peerHandle?.let { peer ->
                networkSpecifier = WifiAwareNetworkSpecifier.Builder(session, peer)
                    .setPskPassphrase(passphrase)
                    .setPort(port)
                    .build()

            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()

        callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                // What does this mean on the publisher?
                Log.d(LOG_TAG, "onAvailable")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                // What does this mean on the publisher?
                Log.d(LOG_TAG, "onCapabilitiesChanged")
            }

            override fun onLost(network: Network) {
                // What does this mean on the publisher?
                Log.d(LOG_TAG, "onLost")
            }
        }

        Log.d(LOG_TAG, "Requesting network")
        callback?.let {
            connectivityManager.requestNetwork(networkRequest, it)
        }

        Log.d(LOG_TAG, "Awaiting network setup")

        Log.d(LOG_TAG, "Waiting for client connection...")
        socket = serverSocket.accept()
        Log.d(LOG_TAG, "Client accepted.")


        Log.d(LOG_TAG, "Reading...")
        read()
    }

    private fun read() {

        val inputStream = socket?.getInputStream()

        val receivedData = ByteArray(64000)
        val read = inputStream?.read(receivedData)

        if (read != -1) {
            val trimmed = Arrays.copyOfRange(receivedData, 0, read ?: 0)

            Log.d(LOG_TAG, "Received HTTP request: " + Hex.toHexString(trimmed))

            val buffer = SessionInputBufferImpl(HttpTransportMetricsImpl(), trimmed.size)
            buffer.bind(ByteArrayInputStream(trimmed))

            val requestParser = DefaultHttpRequestParser(buffer)

            val httpRequest: HttpRequest
            try {
                httpRequest = requestParser.parse()

                if (httpRequest is HttpEntityEnclosingRequest) {
                    val contentLengthStrategy = StrictContentLengthStrategy.INSTANCE
                    val len = contentLengthStrategy.determineLength(httpRequest)
                    val contentStream = when (len) {
                        ContentLengthStrategy.CHUNKED.toLong() -> ChunkedInputStream(buffer)
                        ContentLengthStrategy.IDENTITY.toLong() -> IdentityInputStream(buffer)
                        else -> ContentLengthInputStream(buffer, len)
                    }
                    val ent = BasicHttpEntity()
                    ent.content = contentStream
                    httpRequest.entity = ent

                    val requestBody = ByteArray(64000)
                    val length = httpRequest.entity.content.read(requestBody)

                    val requestBodyTrimmed = Arrays.copyOfRange(requestBody, 0, length)

                    Log.d(
                        LOG_TAG,
                        "Parsed HTTP request body: " + Hex.toHexString(requestBodyTrimmed)
                    )

                    executorEventListener?.onReceive(requestBodyTrimmed)
                } else {
                    Log.d(
                        LOG_TAG,
                        "Http request type unexpected: " + httpRequest.javaClass.simpleName
                    )
                    executorEventListener?.onReceive(trimmed)
                }

            } catch (e: Exception) {
                Log.d(LOG_TAG, "Failed to parse http request: " + e.message, e)
                executorEventListener?.onReceive(trimmed)
            }
        }
    }

    override fun onPublishStarted(session: PublishDiscoverySession) {
        Log.d(LOG_TAG, "onPublishStarted $session")
        this.publishSession = session
    }

    @Synchronized
    override fun onMessageReceived(peerHandle: PeerHandle?, message: ByteArray?) {
        Log.d(LOG_TAG, "onMessageReceived $peerHandle " + message?.let { String(it) })
        if (this.peerHandle == null) {
            Log.d(LOG_TAG, "Setting peer")
            this.peerHandle = peerHandle
        }
        doAsync {
            setup()
        }
    }

    override fun setEventListener(eventListener: IExecutorEventListener?) {
        Log.d(LOG_TAG, "setEventListener")
        executorEventListener = eventListener
    }

    override fun closeConnection() {
        Log.d(LOG_TAG, "Close connection")
        publishSession?.close()
        if (socket?.isConnected == true) {
            socket?.close()
        }
        wifiTransportManager.close()
    }

    override fun inititalize(publicKeyHash: ByteArray?) {
        Log.d(LOG_TAG, "initialize")
        // Initialized not used waiting for wifi session callback
    }

    fun publish(session: WifiAwareSession) {
        this.wifiAwareSession = session
        wifiAwareSession?.let {
            Log.d(LOG_TAG, "Publish: $it")
            Log.d(LOG_TAG, "Initializing socket communication")
            it.publish(config, this, null)
            executorEventListener?.onEvent(
                EventType.STATE_READY_FOR_TRANSMISSION.description,
                EventType.STATE_READY_FOR_TRANSMISSION.ordinal
            )

        }
    }

    override fun write(data: ByteArray?) {
        doAsync {
            Log.d(LOG_TAG, "Write data: ${Hex.toHexString(data)}")
            data?.let {
                val header = "HTTP/1.1 200 OK\r\n".toByteArray()
                val length = ("Content-length: " + data.size + "\r\n").toByteArray()
                val type = "Content-type: application/CBOR\r\n".toByteArray()
                val separator = "\r\n".toByteArray()

                val message = header + length + type + separator + data

                socket?.getOutputStream()?.write(message)
                socket?.getOutputStream()?.flush()
            }
            // Inform UI that the transfer is completed
            uiThread {
                Log.d(LOG_TAG, "Transfer completed")
                wifiTransportManager.getTransportProgressDelegate()
                    .onEvent(EventType.TRANSFER_COMPLETE, "Transfer completed")
            }
        }
    }

    override fun close() {
        Log.d(LOG_TAG, "Close")
        closeConnection()
    }
}