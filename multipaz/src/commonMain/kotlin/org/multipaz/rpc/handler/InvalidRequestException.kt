package org.multipaz.rpc.handler

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcException

@RpcException
@CborSerializable
class InvalidRequestException(override val message: String?) : RuntimeException(message) {
    companion object
}
