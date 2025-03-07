package org.multipaz.device

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.securearea.KeyAttestation

/**
 * An open-ended statement that can be wrapped in [DeviceAssertion].
 */
@CborSerializable
sealed class Assertion {
    companion object
}