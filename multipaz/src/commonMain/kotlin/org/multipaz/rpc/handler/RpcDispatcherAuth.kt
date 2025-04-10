package org.multipaz.rpc.handler

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem

/**
 * [RpcDispatcher] implementation that dispatches RPC call to a [base] dispatcher
 * after adding RPC method authorization to it.
 */
class RpcDispatcherAuth(
    val base: RpcDispatcher,
    private val rpcAuthIssuer: RpcAuthIssuer
): RpcDispatcher {
    override val exceptionMap: RpcExceptionMap
        get() = base.exceptionMap

    override suspend fun dispatch(target: String, method: String, args: DataItem): List<DataItem> {
        val payload = Bstr(Cbor.encode(args))
        val message = rpcAuthIssuer.auth(target, method, payload).also {
            check(it["payload"] === payload) {
                "Authenticated message 'payload' field must contain raw RPC message"
            }
        }
        return base.dispatch(target, method, message)
    }
}