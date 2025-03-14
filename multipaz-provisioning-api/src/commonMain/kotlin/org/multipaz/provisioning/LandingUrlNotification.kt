package org.multipaz.provisioning

import org.multipaz.cbor.annotation.CborSerializable

@CborSerializable
class LandingUrlNotification(
    val baseUrl: String
) {
    companion object
}