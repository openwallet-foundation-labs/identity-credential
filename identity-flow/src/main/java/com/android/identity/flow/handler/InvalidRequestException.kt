package com.android.identity.flow.handler

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowException

@FlowException
@CborSerializable
class InvalidRequestException(message: String?) : RuntimeException(message) {
    companion object
}
