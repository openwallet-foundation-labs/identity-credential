package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import kotlinx.datetime.Instant

/**
 * Information about the capabilities of the wallet server, for the wallet application.
 *
 * If [waitForNotificationSupported] is `false` it means that the wallet application
 * should use push notifications instead of long polling on [WalletServer.waitForNotification].
 * This is normally only set to `true` for development instances of the server because
 * of the fact that things like [WalletServer.waitForNotification] doesn't work efficiently
 * at scale or when the application is not running.
 *
 * @property generatedAt The point in time this data was generated.
 * @property waitForNotificationSupported Whether the [WalletServer.waitForNotification] method is
 * supported by the server.
 */
@CborSerializable
data class WalletServerCapabilities(
    val generatedAt: Instant,
    val waitForNotificationSupported: Boolean,
) {
    companion object
}
