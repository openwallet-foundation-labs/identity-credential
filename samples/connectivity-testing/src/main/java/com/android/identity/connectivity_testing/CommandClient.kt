package com.android.identity.connectivity_testing

import com.android.identity.util.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommandClient(
    val serverAddress: String,
    val serverPort: Int,
    onIterationDo: suspend (commandFromServer: String) -> String
) {
    companion object {
        private const val TAG = "CommandClient"
    }

    init {
        val selectorManager = SelectorManager(Dispatchers.IO)

        CoroutineScope(Dispatchers.IO).launch {
            val socket = aSocket(selectorManager).tcp().connect(serverAddress, serverPort)
            val receiveChannel = socket.openReadChannel()
            val sendChannel = socket.openWriteChannel(autoFlush = true)
            sendChannel.writeStringUtf8("Hello\n")
            while (true) {
                val commandFromServer = receiveChannel.readUTF8Line()
                Logger.i(TAG, "Received cmd '$commandFromServer'")
                val iterationResult = onIterationDo(commandFromServer!!)
                sendChannel.writeStringUtf8(iterationResult + "\n")
            }
        }
    }
}