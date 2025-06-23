package org.multipaz.testapp.provisioning.backend

import org.multipaz.cbor.annotation.CborSerializable

@CborSerializable
data class ServerData(
    val clientId: String,
    val deviceAttestationId: String
) {
    companion object
}