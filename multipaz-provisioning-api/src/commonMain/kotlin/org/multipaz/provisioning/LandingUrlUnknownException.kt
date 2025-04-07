package org.multipaz.provisioning

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcException

@RpcException
@CborSerializable
class LandingUrlUnknownException(override val message: String) : Exception(message) {
    companion object
}