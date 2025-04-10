package org.multipaz.rpc.handler

import org.multipaz.cbor.DataItem

/**
 * Generated flow interface implementations use this interface to dispatch
 * method calls to the flow implementations, potentially across the network.
 */
interface RpcDispatcher {
    val exceptionMap: RpcExceptionMap
    suspend fun dispatch(target: String, method: String, args: DataItem): List<DataItem>
}