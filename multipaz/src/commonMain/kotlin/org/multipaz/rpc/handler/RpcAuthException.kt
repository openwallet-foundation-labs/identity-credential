package org.multipaz.rpc.handler

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcException

/** RPC authorization failure. */
@RpcException
@CborSerializable
class RpcAuthException(
    override val message: String?,
    val rpcAuthError: RpcAuthError
) : RuntimeException(message) {
    companion object
}