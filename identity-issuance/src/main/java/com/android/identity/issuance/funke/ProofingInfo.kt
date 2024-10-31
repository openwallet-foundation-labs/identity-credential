package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable

@CborSerializable
data class ProofingInfo(
    val requestUri: String?,
    val pkceCodeVerifier: String,
    val landingUrl: String,
    val authSession: String?,
    val openid4VpPresentation: String?,
)