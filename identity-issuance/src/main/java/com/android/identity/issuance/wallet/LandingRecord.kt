package com.android.identity.issuance.wallet

import com.android.identity.cbor.annotation.CborSerializable

@CborSerializable
class LandingRecord(
    val clientId: String,
    var resolved: String? = null
) {
    companion object
}