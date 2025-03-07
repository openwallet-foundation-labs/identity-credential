package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.flow.annotation.FlowException

@FlowException
@CborSerializable
class LandingUrlUnknownException(override val message: String) : Exception(message) {
    companion object
}