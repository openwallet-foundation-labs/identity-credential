package com.android.identity.issuance.remote

import com.android.identity.cbor.annotation.CborSerializable

@CborSerializable
data class WalletServerConnectionData(
    val clientId: String,
    val deviceAttestationId: String
) {
    companion object
}