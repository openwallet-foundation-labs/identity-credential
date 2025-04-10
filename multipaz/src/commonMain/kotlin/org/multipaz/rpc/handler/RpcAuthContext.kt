package org.multipaz.rpc.handler

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * An object added to the current coroutine context based on the successful processing of RPC
 * call authorization.
 *
 * See [RpcAuthInspector].
 */
class RpcAuthContext(
    private val clientId: String
): CoroutineContext.Element {
    object Key: CoroutineContext.Key<RpcAuthContext>

    override val key: CoroutineContext.Key<RpcAuthContext>
        get() = Key

    companion object {
        suspend fun getClientId(): String {
            return coroutineContext[Key]!!.clientId
        }
    }
}