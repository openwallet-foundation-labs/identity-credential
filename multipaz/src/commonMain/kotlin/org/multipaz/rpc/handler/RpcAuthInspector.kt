package org.multipaz.rpc.handler

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.DataItem
import kotlin.coroutines.CoroutineContext

/**
 * RPC call authorization.
 *
 * A back-end object that requires RPC call authorization must implement this interface.
 */
interface RpcAuthInspector {
    /**
     * Checks RPC authorization.
     *
     * Before an RPC call is made this method is called to do authorization check. Cbor-serialized
     * RPC call parameters (back-end state and call arguments) are passed as [payload].
     * [authMessage] contains fields that are specific for a particular authorization type.
     * If authorization check succeeds, this method must return an authorization context
     * containing authorization-related data (it typically should include [RpcAuthContext]).
     * This context is added to the coroutine context for the RPC call. If authorization check
     * fails, an exception (typically [RpcAuthException] or [RpcAuthNonceException])
     * __must__ be thrown.
     */
    suspend fun authCheck(
        target: String,
        method: String,
        payload: Bstr,
        authMessage: DataItem,
    ): CoroutineContext
}