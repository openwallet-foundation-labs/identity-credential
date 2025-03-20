package org.multipaz.wallet.provisioning.remote

import org.multipaz.cbor.annotation.CborSerializable

@CborSerializable
data class WalletServerConnectionData(
    val clientId: String,
    val deviceAttestationId: String
) {
    companion object
}