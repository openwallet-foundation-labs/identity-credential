package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.flow.annotation.FlowException

/**
 * Represents a generic error in issuer with the human-readable message.
 */
@FlowException
@CborSerializable
class IssuingAuthorityException(override val message: String) : Exception(message) {
    companion object
}