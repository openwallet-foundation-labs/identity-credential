package org.multipaz.rpc.client

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.rpc.handler.RpcDispatcher
import org.multipaz.rpc.handler.RpcNotifier
import kotlin.concurrent.Volatile

/**
 * Base class for generated RPC stubs.
 *
 * Should only be used by the generated code.
 */
abstract class RpcStub(
    val rpcEndpoint: String, // RPC endpoint name from `RpcState` annotation endpoint parameter
    val rpcDispatcher: RpcDispatcher,
    val rpcNotifier: RpcNotifier,
    @Volatile var rpcState: DataItem  // Opaque back-end data.
) {
    fun toCbor(): ByteArray = Cbor.encode(toDataItem())

    fun toDataItem(): DataItem = buildCborMap {
        put("endpoint", rpcEndpoint)
        put("state", rpcState)
    }

    companion object {
        fun rpcParameter(obj: Any) = buildCborArray {
            // If this fails, non-RPC-stub is being passed as a parameter of RPC stub method
            obj as RpcStub
            add(obj.rpcEndpoint)
            add(obj.rpcState)
        }
    }
}