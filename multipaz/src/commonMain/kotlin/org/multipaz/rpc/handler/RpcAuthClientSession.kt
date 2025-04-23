package org.multipaz.rpc.handler

import kotlinx.io.bytestring.ByteString
import kotlin.coroutines.CoroutineContext

class RpcAuthClientSession: CoroutineContext.Element {
    object Key : CoroutineContext.Key<RpcAuthClientSession>

    override val key: CoroutineContext.Key<RpcAuthClientSession>
        get() = Key

    var nonce: ByteString = EMPTY_NONCE
        internal set

    companion object {
        val EMPTY_NONCE = ByteString()
    }
}
