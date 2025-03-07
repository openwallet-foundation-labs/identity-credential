package org.multipaz.flow.handler

import org.multipaz.cbor.DataItem

/**
 * Generated flow interface implementations use this interface to dispatch
 * method calls to the flow implementations, potentially across the network.
 */
interface FlowDispatcher {
    val exceptionMap: FlowExceptionMap
    suspend fun dispatch(flow: String, method: String, args: List<DataItem>): List<DataItem>
}