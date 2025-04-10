package org.multipaz.rpc.handler

import org.multipaz.cbor.Cbor
import org.multipaz.rpc.transport.HttpTransport
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.buildCborArray

/** [RpcPoll] implementation that works through HTTP. */
class RpcPollHttp(private val transport: HttpTransport): RpcPoll {
    override suspend fun poll(consumeToken: String, pollKeys: List<RpcPoll.PollKey>): RpcPoll.PollResult {
        val message = buildCborArray {
            add(consumeToken)
            pollKeys.forEach { flowRef ->
                add(flowRef.target)
                add(flowRef.opaqueState)
            }
        }
        val result = try {
            val response = transport.post("_/poll", ByteString(Cbor.encode(message)))
            Cbor.decode(response.toByteArray()).asArray
        } catch(err: HttpTransport.TimeoutException) {
            throw RpcPoll.TimeoutException()
        }
        if (result.isEmpty()) {
            throw RpcPoll.TimeoutException()
        }
        return RpcPoll.PollResult(
            consumeToken = result[0].asTstr,
            index = result[1].asNumber.toInt(),
            notification = result[2]
        )
    }
}