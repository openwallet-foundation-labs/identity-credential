package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.device.DeviceAssertion
import com.android.identity.issuance.CredentialFormat
import com.android.identity.securearea.KeyAttestation

@CborSerializable
class CredentialRequestSet(
    val format: CredentialFormat,
    val keyAttestations: List<KeyAttestation>,
    val keysAssertion: DeviceAssertion  // holds AssertionBindingKeys
) {
    companion object
}
