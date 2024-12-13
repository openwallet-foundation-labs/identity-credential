package com.android.identity.device

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.securearea.KeyAttestation

/**
 * An open-ended statement that can be wrapped in [DeviceAssertion].
 */
@CborSerializable
sealed class Assertion {
    companion object
}