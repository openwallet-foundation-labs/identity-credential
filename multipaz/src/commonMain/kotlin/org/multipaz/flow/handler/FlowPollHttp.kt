package org.multipaz.flow.handler

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.flow.transport.HttpTransport
import kotlinx.io.bytestring.ByteString

/** [FlowPoll] implementation that works through HTTP. */
class FlowPollHttp(private val transport: HttpTransport): FlowPoll {
    override suspend fun poll(consumeToken: String, flows: List<FlowPoll.PollKey>): FlowPoll.PollResult {
        val builder = CborArray.builder()
        builder.add(consumeToken)
        flows.forEach {flowRef ->
            builder.add(flowRef.flowName)
            builder.add(flowRef.opaqueState)
        }
        val result = try {
            val response = transport.post("_/poll", ByteString(Cbor.encode(builder.end().build())))
            Cbor.decode(response.toByteArray()).asArray
        } catch(err: HttpTransport.TimeoutException) {
            throw FlowPoll.TimeoutException()
        }
        if (result.isEmpty()) {
            throw FlowPoll.TimeoutException()
        }
        return FlowPoll.PollResult(
            consumeToken = result[0].asTstr,
            index = result[1].asNumber.toInt(),
            notification = result[2]
        )
    }
}