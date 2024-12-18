package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import kotlinx.datetime.Instant

/**
 * Information about the capabilities of the wallet server, for the wallet application.
 *
 * @property generatedAt The point in time this data was generated.
 */
@CborSerializable
data class WalletServerCapabilities(
    val generatedAt: Instant
) {
    companion object
}
