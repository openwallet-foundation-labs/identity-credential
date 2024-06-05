package com.android.identity.flow.handler

import com.android.identity.cbor.DataItem

/**
 * Generated flow interface implementations use this interface to dispatch
 * getters and calls to the flow implementations, potentially across
 * the network.
 */
interface FlowHandler {
    suspend fun get(flow: String, method: String): DataItem
    suspend fun post(flow: String, method: String, args: List<DataItem>): List<DataItem>
}