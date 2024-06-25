package com.android.identity.wallet.server

import kotlinx.datetime.Instant
import com.android.identity.cbor.annotation.CborSerializable
import kotlinx.io.bytestring.ByteString

/** Data stored in authentication cookie for admin interface. */
@CborSerializable
data class AdminAuthCookie(
    val expiration: Instant,
    val passwordHash: ByteString
) {
    companion object
}
