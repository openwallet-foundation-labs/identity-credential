package org.multipaz.legacyprovisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.device.DeviceAssertion
import org.multipaz.legacyprovisioning.CredentialFormat
import org.multipaz.securearea.KeyAttestation

@CborSerializable
class CredentialRequestSet(
    val format: CredentialFormat,
    val keyAttestations: List<KeyAttestation>,
    val keysAssertion: DeviceAssertion  // holds AssertionBindingKeys
) {
    companion object
}
