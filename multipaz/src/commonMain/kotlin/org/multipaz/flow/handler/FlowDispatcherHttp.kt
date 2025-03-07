package org.multipaz.flow.handler

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.DataItem
import org.multipaz.flow.transport.HttpTransport
import kotlinx.io.bytestring.ByteString

/** [FlowDispatcher] implementation that dispatches flow method calls through HTTP. */
class FlowDispatcherHttp(
    private val transport: HttpTransport,
    override val exceptionMap: FlowExceptionMap
) : FlowDispatcher {
    override suspend fun dispatch(flow: String, method: String, args: List<DataItem>): List<DataItem> {
        val builder = CborArray.builder()
        args.forEach { builder.add(it) }
        val response = transport.post("$flow/$method", ByteString(Cbor.encode(builder.end().build())))
        return Cbor.decode(response.toByteArray()).asArray
    }
}