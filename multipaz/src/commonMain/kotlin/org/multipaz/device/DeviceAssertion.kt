package org.multipaz.device

import org.multipaz.cbor.annotation.CborSerializable
import kotlinx.io.bytestring.ByteString

/**
 * [Assertion] and additional data that can be used to validate the statement in the assertion.
 *
 * [DeviceAssertion] validation requires access to the corresponding [DeviceAttestation].
 *
 * Note that unlike [DeviceAttestation], [DeviceAssertion] is vouched for by the wallet app
 * (so it is important that [DeviceAttestation] was validated at some point, as it is the
 * [DeviceAttestation] which is the expression of the platform vouching for the wallet app).
 */
@CborSerializable
data class DeviceAssertion(
    /**
     * Cbor-serialized [Assertion], signed over by platformAssertion.
     *
     * This is kept serialized, so that serialization discrepancies do not affect our ability
     * to validate [platformAssertion].
     */
    val assertionData: ByteString,

    /**
     * Platform-specific "signature" validating integrity of the [assertionData].
     */
    val platformAssertion: ByteString,
) {
    val assertion: Assertion
        get() = Assertion.fromCbor(assertionData.toByteArray())

    companion object
}