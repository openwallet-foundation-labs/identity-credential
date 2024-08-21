package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowException

/**
 * Represents a generic error in issuer with the human-readable message.
 */
@FlowException
@CborSerializable
class IssuingAuthorityException(message: String?) : Exception(message) {
    companion object
}