package org.multipaz.flow.handler

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.flow.annotation.FlowException

@FlowException
@CborSerializable
class InvalidRequestException(override val message: String?) : RuntimeException(message) {
    companion object
}
