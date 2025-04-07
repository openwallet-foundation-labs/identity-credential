package org.multipaz.rpc.handler

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem
import org.multipaz.rpc.transport.HttpTransport
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.buildCborArray

/**
 * Implements [HttpTransport] based on the given [RpcDispatcher] and [RpcPoll]. Useful
 * to route incoming HTTP requests on the server.
 */
class HttpHandler(
    private val dispatcher: RpcDispatcher,
    private val rpcPoll: RpcPoll
): HttpTransport {
    override suspend fun post(url: String, data: ByteString): ByteString {
        val (target, method) = url.split("/")
        val args = Cbor.decode(data.toByteArray())
        val result = if (target == "_") {
            handlePoll(args.asArray)
        } else {
            dispatcher.dispatch(target, method, args)
        }
        return ByteString(Cbor.encode(
            buildCborArray {
                result.forEach { add(it) }
            }
        ))
    }

    private suspend fun handlePoll(args: List<DataItem>): List<DataItem> {
        val consumedToken = args[0].asTstr
        val pollKeys = mutableListOf<RpcPoll.PollKey>()
        for (i in 1..<args.size step 2) {
            pollKeys.add(RpcPoll.PollKey(
                target = args[i].asTstr,
                opaqueState = args[i+1]
            ))
        }
        try {
            val result = rpcPoll.poll(consumedToken, pollKeys)
            val resultList = mutableListOf<DataItem>()
            resultList.add(result.consumeToken.toDataItem())
            resultList.add(result.index.toDataItem())
            resultList.add(result.notification)
            return resultList.toList()
        } catch (err: RpcPoll.TimeoutException) {
            // Signal using empty message with 200 status
            return listOf()
        }
    }
}