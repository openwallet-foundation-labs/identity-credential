package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable

@CborSerializable
data class ProofingInfo(
    val authorizeUrl: String,
    val pkceCodeVerifier: String,
    val landingUrl: String
)