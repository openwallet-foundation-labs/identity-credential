package org.multipaz.legacyprovisioning

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcException

/**
 * Represents a generic error in issuer with the human-readable message.
 */
@RpcException
@CborSerializable
class IssuingAuthorityException(override val message: String) : Exception(message) {
    companion object
}