package org.multipaz.issuance.remote

import org.multipaz.cbor.annotation.CborSerializable

@CborSerializable
data class WalletServerConnectionData(
    val clientId: String,
    val deviceAttestationId: String
) {
    companion object
}