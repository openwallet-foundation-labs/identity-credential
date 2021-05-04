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

package com.ul.ims.gmdl.wifiofflinetransfer.subscriber

import android.annotation.TargetApi
import android.net.*
import android.net.wifi.aware.*
import android.os.Build
import android.util.Log
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import com.ul.ims.gmdl.wifiofflinetransfer.WifiTransportManager
import org.apache.http.HttpHeaders
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.entity.ContentLengthStrategy
import org.apache.http.impl.entity.StrictContentLengthStrategy
import org.apache.http.impl.io.*
import org.bouncycastle.util.encoders.Hex
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

@TargetApi(29)
class WifiAwareServiceSubscriber internal constructor(
    serviceName: String,
    private val passphrase: String,
    private val connectivityManager: ConnectivityManager,
    private val wifiTransportManager: WifiTransportManager
) : ITransportLayer, DiscoverySessionCallback() {

    companion object {
        private const val LOG_TAG = "WifiAwareServiceSubscriber"
    }

    private val config: SubscribeConfig = SubscribeConfig.Builder()
        .setServiceName(serviceName)
        .build()

    // Listener to notify the executor layer that we've got data
    private var executorEventListener: IExecutorEventListener? = null

    private var peerHandle: PeerHandle? = null

    private var discoverySession: SubscribeDiscoverySession? = null

    private var socket: Socket? = null

    private var callback: ConnectivityManager.NetworkCallback? = null

    private var wifiAwareSession: WifiAwareSession? = null

    fun setup() {

        // https://developer.android.com/guide/topics/connectivity/wifi-aware.html#create_a_connection

        // step 2 (sending message) is done inside discovery

        // step 3 and 4 are for the publisher

        if (peerHandle == null || discoverySession == null) {
            return
        }

        // step 5

        var networkSpecifier: NetworkSpecifier? = null
        discoverySession?.let { session ->
            peerHandle?.let { peer ->
                networkSpecifier = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    @Suppress("DEPRECATION")
                    session.createNetworkSpecifierPassphrase(peer, passphrase)
                } else {
                    WifiAwareNetworkSpecifier.Builder(session, peer)
                        .setPskPassphrase(passphrase)
                        .build()
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()

        callback = object : ConnectivityManager.NetworkCallback() {

            private var peerIpv6Addr: Inet6Address? = null
            private var peerPort: Int = 0

            override fun onAvailable(network: Network) {
                Log.d(LOG_TAG, "onAvailable")
            }

            @Synchronized
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                Log.d(LOG_TAG, "onCapabilitiesChanged")

                if (socket == null) {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                        if (networkCapabilities.transportInfo is WifiAwareNetworkInfo) {
                            val peerAwareInfo =
                                networkCapabilities.transportInfo as? WifiAwareNetworkInfo

                            peerAwareInfo?.let { peerInfo ->
                                peerIpv6Addr = peerInfo.peerIpv6Addr
                                peerPort = peerInfo.port
                            }

                            Log.d(LOG_TAG, "onCapabilitiesChanged with $peerIpv6Addr:$peerPort")

                            socket = network.socketFactory.createSocket(peerIpv6Addr, peerPort)
                            // Send to UI that the connection is ready
                            executorEventListener?.onEvent(
                                EventType.STATE_READY_FOR_TRANSMISSION.description,
                                EventType.STATE_READY_FOR_TRANSMISSION.ordinal
                            )
                        } else {
                            return
                        }
                    }

                }
            }

            override fun onLost(network: Network) {
                Log.d(LOG_TAG, "onLost")
                socket = null
            }
        }

        Log.d(LOG_TAG, "Requesting network")
        callback?.let {
            connectivityManager.requestNetwork(networkRequest, it)
        }
        Log.d(LOG_TAG, "Awaiting connection")

    }

    private fun writeData(data: ByteArray?) {
        data?.let {
            // IPv6 addresses need to be enclosed in square brackets [].
            //  e.g. https://issues.apache.org/jira/browse/HTTPCLIENT-1795
            var host = "[" + (socket?.remoteSocketAddress as InetSocketAddress).hostString + "]"
            // https://tools.ietf.org/html/rfc6874 2. Specification says % needs to be escaped
            host = host.replace("%", "%25")
            host = host + ":" + (socket?.remoteSocketAddress as InetSocketAddress).port
            Log.d(LOG_TAG, "writeData host: $host")

            val httpHeaderStart = "POST /mDL HTTP/1.1\r\n".toByteArray()
            val httpHeaderHost = "Host: $host\r\n".toByteArray()
            val httpHeaderContentLength = ("Content-Length: " + data.size + "\r\n").toByteArray()
            val httpHeaderContentType = "Content-Type: application/CBOR\r\n".toByteArray()
            //final byte[] httpPayload = data;
            val httpEnd = "\r\n".toByteArray()

            val message =
                httpHeaderStart + httpHeaderHost + httpHeaderContentLength + httpHeaderContentType + httpEnd + data + httpEnd

            Log.d(LOG_TAG, "Sending HTTP: ${String(message)}")

            socket?.getOutputStream()?.write(message)
            socket?.getOutputStream()?.flush()
        }
    }

    private fun read() {

        val inputStream = socket?.getInputStream()

        val receivedData = ByteArray(64000)
        var trimmed: ByteArray
        var read: Int?
        var receivedContent = byteArrayOf()

        try {

            read = inputStream?.read(receivedData)
            if (read != -1) {
                trimmed = receivedData.copyOfRange(0, read ?: 0)
                Log.d(LOG_TAG, "Received HTTP response: " + Hex.toHexString(trimmed))

                val buffer = SessionInputBufferImpl(HttpTransportMetricsImpl(), trimmed.size)
                buffer.bind(ByteArrayInputStream(trimmed))

                val responseParser = DefaultHttpResponseParser(buffer)
                val httpResponse = responseParser.parse()

                // responseParser only parses the header and not the entity body. Inject it here.
                val contentTypeHeader = httpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE)

                val contentLengthStrategy = StrictContentLengthStrategy.INSTANCE
                val len = contentLengthStrategy.determineLength(httpResponse)

                val contentStream = when (len) {
                    ContentLengthStrategy.CHUNKED.toLong() -> ChunkedInputStream(buffer)
                    ContentLengthStrategy.IDENTITY.toLong() -> IdentityInputStream(buffer)
                    else -> ContentLengthInputStream(buffer, len)
                }
                val ent = BasicHttpEntity()
                ent.content = contentStream
                ent.contentType = contentTypeHeader
                httpResponse.entity = ent

                val responseBody = ByteArray(64000)

                val length = httpResponse.entity.content.read(responseBody)
                val responseBodyTrimmed = responseBody.copyOfRange(0, length)

                Log.d(LOG_TAG, "contentLength: $len readLength: $length")

                receivedContent += responseBodyTrimmed


                Log.d(LOG_TAG, "content size: ${receivedContent.size} content length: $len")
                Log.d(LOG_TAG, "ReceivedContent: " + Hex.toHexString(receivedContent))

                // Keep reading until receive all data
                while (read != -1 && receivedContent.size < len) {
                    read = inputStream?.read(receivedData)
                    trimmed = receivedData.copyOfRange(0, read ?: 0)
                    Log.d(LOG_TAG, "Response: " + Hex.toHexString(trimmed))

                    receivedContent += trimmed.copyOfRange(0, trimmed.size)

                    Log.d(LOG_TAG, "content size: ${receivedContent.size} content length: $len")
                    Log.d(LOG_TAG, "ReceivedContent: " + Hex.toHexString(receivedContent))
                }
            }
            executorEventListener?.onReceive(receivedContent)

        } catch (e: IOException) {
            Log.e(LOG_TAG, e.message, e)
            wifiTransportManager.getTransportProgressDelegate().onEvent(EventType.ERROR,
                EventType.ERROR.description
            )
        } finally {
            inputStream?.close()
        }
    }

    /**
     * This is synchronized because onServiceDiscovered can be called multiple times for the
     * same peer.
     */
    @Synchronized
    override fun onServiceDiscovered(
        peerHandle: PeerHandle,
        serviceSpecificInfo: ByteArray,
        matchFilter: List<ByteArray>
    ) {
        Log.d(LOG_TAG, "Peer discovered $peerHandle")

        if (this.peerHandle == null) {
            this.peerHandle = peerHandle

            discoverySession?.sendMessage(
                peerHandle,
                0,
                "hoi".toByteArray(StandardCharsets.UTF_8)
            )
        }
    }

    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
        Log.d(LOG_TAG, "Subscribe started $session")
        this.discoverySession = session
    }

    override fun onMessageSendSucceeded(messageId: Int) {
        Log.d(LOG_TAG, "MessageId $messageId successfully sent")
        super.onMessageSendSucceeded(messageId)
        doAsync {
            setup()
        }
    }

    override fun onMessageSendFailed(messageId: Int) {
        Log.d(LOG_TAG, "MessageId $messageId failed")
        peerHandle = null
        discoverySession?.close()
        executorEventListener?.onEvent(
            EventType.ERROR.description,
            EventType.ERROR.ordinal
        )
    }

    override fun onMessageReceived(peerHandle: PeerHandle?, message: ByteArray?) {
        Log.d(
            LOG_TAG,
            "Message received peerHandle: $peerHandle message: ${Hex.toHexString(message)}"
        )
    }

    override fun setEventListener(eventListener: IExecutorEventListener?) {
        Log.d(LOG_TAG, "setEventListener")
        executorEventListener = eventListener
    }

    override fun closeConnection() {
        Log.d(LOG_TAG, "Close connection")
        discoverySession?.close()
        if (socket?.isConnected == true) {
            socket?.close()
        }
        wifiTransportManager.close()
    }

    override fun inititalize(publicKeyHash: ByteArray?) {
        Log.d(LOG_TAG, "initialize")
        // Initialized not used waiting for wifi session callback
    }

    fun subscribe(session: WifiAwareSession) {
        this.wifiAwareSession = session

        wifiAwareSession?.let {
            Log.d(LOG_TAG, "Subscribe: $it")
            Log.d(LOG_TAG, "Initializing socket communication")
            it.subscribe(config, this, null)
        }
    }

    override fun write(data: ByteArray?) {
        Log.d(LOG_TAG, "Write data")
        // Send request to the Holder
        doAsync {
            writeData(data)
            uiThread {
                Log.d(LOG_TAG, "Data written")
                wifiTransportManager.getTransportProgressDelegate()
                    .onEvent(EventType.TRANSFER_IN_PROGRESS, "")
            }
        // Start to listener the response
            read()
        }
    }

    override fun close() {
        Log.d(LOG_TAG, "Close")
        closeConnection()
    }
}