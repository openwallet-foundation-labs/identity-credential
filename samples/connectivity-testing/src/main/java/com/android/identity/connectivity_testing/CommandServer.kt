package com.android.identity.connectivity_testing

import com.android.identity.util.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.toJavaAddress
import io.ktor.util.network.port
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.NetworkInterface

class CommandServer(
    onClientConnected: suspend () -> Unit,
    onIterationSetup: suspend (iterationNumber: Int) -> String,
    onIterationResult: suspend (cmdFromClient: String) -> Unit
) {
    companion object {
        private const val TAG = "CommandServer"
    }

    lateinit var localAddress: String
    var localAddressPort: Int = 0
    lateinit var serverSocket: ServerSocket

    init {
        for (iface in NetworkInterface.getNetworkInterfaces()) {
            for (inetAddress in iface.inetAddresses) {
                if (!inetAddress.isLoopbackAddress) {
                    val address = inetAddress.hostAddress
                    if (address != null && address.indexOf(':') < 0) {
                        localAddress = address
                    }
                }
            }
        }
        if (!this::localAddress.isInitialized) {
            throw IllegalStateException("Unable to determine local address")
        }

        val selectorManager = SelectorManager(Dispatchers.IO)
        serverSocket = aSocket(selectorManager).tcp().bind(localAddress, 0)
        localAddressPort = serverSocket.localAddress.toJavaAddress().port

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = serverSocket.accept()
                onClientConnected()
                Logger.i(TAG, "Client connected")
                val receiveChannel = socket.openReadChannel()
                val sendChannel = socket.openWriteChannel(autoFlush = true)
                val initialCmd = receiveChannel.readUTF8Line()
                if (initialCmd != "Hello") {
                    throw IllegalStateException("Expected 'Hello', got '$initialCmd'")
                }
                var iterationNumber = 0
                while (true) {
                    sendChannel.writeStringUtf8(onIterationSetup(iterationNumber) + "\n")
                    val cmd = receiveChannel.readUTF8Line()
                    Logger.i(TAG, "Received cmd '$cmd'")
                    onIterationResult(cmd!!)
                    iterationNumber += 1
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Server failed", e)
            }
        }
    }
}