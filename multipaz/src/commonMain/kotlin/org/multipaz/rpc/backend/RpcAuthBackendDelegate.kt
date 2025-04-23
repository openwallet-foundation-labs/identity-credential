package org.multipaz.rpc.backend

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.DataItem
import org.multipaz.rpc.handler.RpcAuthInspector
import kotlin.coroutines.CoroutineContext

/**
 * Convenience object to add default [RpcAuthInspector] implementation.
 *
 * Use `RpcAuthInspector by RpcAuthBackendDelegate` inheritance clause to "mix in" delegating to
 * [RpcAuthInspector] defined in the current [BackendEnvironment].
 */
object RpcAuthBackendDelegate: RpcAuthInspector {
    override suspend fun authCheck(
        target: String,
        method: String,
        payload: Bstr,
        authMessage: DataItem
    ): CoroutineContext =
        BackendEnvironment.getInterface(RpcAuthInspector::class)!!.authCheck(
            target,
            method,
            payload,
            authMessage
        )
}