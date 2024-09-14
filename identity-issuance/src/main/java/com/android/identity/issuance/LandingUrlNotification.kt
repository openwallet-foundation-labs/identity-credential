package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable

@CborSerializable
class LandingUrlNotification(
    val baseUrl: String
) {
    companion object
}