package org.multipaz.rpc.handler

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.rpc.transport.HttpTransport
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Bstr

/** [RpcDispatcher] implementation that dispatches flow method calls through HTTP. */
class RpcDispatcherHttp(
    private val transport: HttpTransport,
    override val exceptionMap: RpcExceptionMap,
) : RpcDispatcher {
    override suspend fun dispatch(target: String, method: String, args: DataItem): List<DataItem> {
        val response = transport.post("$target/$method", ByteString(Cbor.encode(args)))
        return Cbor.decode(response.toByteArray()).asArray
    }
}