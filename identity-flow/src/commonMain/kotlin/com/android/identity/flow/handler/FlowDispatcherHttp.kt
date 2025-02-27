package com.android.identity.flow.handler

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.DataItem
import com.android.identity.flow.transport.HttpTransport

/** [FlowDispatcher] implementation that dispatches flow method calls through HTTP. */
class FlowDispatcherHttp(
    private val transport: HttpTransport,
    override val exceptionMap: FlowExceptionMap
) : FlowDispatcher {
    override suspend fun dispatch(flow: String, method: String, args: List<DataItem>): List<DataItem> {
        val builder = CborArray.builder()
        args.forEach { builder.add(it) }
        val response = transport.post("$flow/$method", Cbor.encode(builder.end().build()))
        return Cbor.decode(response).asArray
    }
}