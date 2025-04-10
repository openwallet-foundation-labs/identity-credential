package org.multipaz.rpc.handler

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.DataItem

/**
 * Interface to issue and add authentication data to RPC messages.
 *
 * Authentication message is checked using [RpcAuthInspector] on the back-end side.
 */
interface RpcAuthIssuer {
    /**
     * Returns authorization for the RPC call defined by [target] and [method] endpoints and call
     * parameters serialized as Cbor array in [payload].
     *
     * All implementations must return a Cbor map that holds `payload` field equal to [payload]
     * parameter that was passed in (and, additionally, other parameters that provide actual RPC
     * call authorization).
     */
    suspend fun auth(target: String, method: String, payload: Bstr): DataItem
}