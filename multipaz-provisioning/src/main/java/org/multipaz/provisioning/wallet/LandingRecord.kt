package org.multipaz.provisioning.wallet

import org.multipaz.cbor.annotation.CborSerializable

@CborSerializable
class LandingRecord(
    val clientId: String,
    var resolved: String? = null
) {
    companion object
}