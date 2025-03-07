package org.multipaz.flow.handler

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem
import org.multipaz.flow.transport.HttpTransport
import kotlinx.io.bytestring.ByteString

/**
 * Implements [HttpTransport] based on the given [FlowDispatcher] and [FlowPoll]. Useful
 * to route incoming HTTP requests on the server.
 */
class HttpHandler(
    private val dispatcher: FlowDispatcher,
    private val flowPoll: FlowPoll
): HttpTransport {
    override suspend fun post(url: String, data: ByteString): ByteString {
        val (target, method) = url.split("/")
        val args = Cbor.decode(data.toByteArray()).asArray
        val result = if (target == "_") {
            handlePoll(args)
        } else {
            dispatcher.dispatch(target, method, args)
        }
        val builder = CborArray.builder()
        result.forEach { builder.add(it) }
        return ByteString(Cbor.encode(builder.end().build()))
    }

    private suspend fun handlePoll(args: List<DataItem>): List<DataItem> {
        val consumedToken = args[0].asTstr
        val pollKeys = mutableListOf<FlowPoll.PollKey>()
        for (i in 1..<args.size step 2) {
            pollKeys.add(FlowPoll.PollKey(
                flowName = args[i].asTstr,
                opaqueState = args[i+1]
            ))
        }
        try {
            val result = flowPoll.poll(consumedToken, pollKeys)
            val resultList = mutableListOf<DataItem>()
            resultList.add(result.consumeToken.toDataItem())
            resultList.add(result.index.toDataItem())
            resultList.add(result.notification)
            return resultList.toList()
        } catch (err: FlowPoll.TimeoutException) {
            // Signal using empty message with 200 status
            return listOf()
        }
    }
}