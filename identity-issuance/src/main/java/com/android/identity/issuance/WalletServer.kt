package com.android.identity.issuance

import com.android.identity.flow.FlowBaseInterface
import com.android.identity.flow.annotation.FlowInterface
import com.android.identity.flow.annotation.FlowMethod

@FlowInterface
interface WalletServer: FlowBaseInterface {
    /**
     * No need to call on client-side if using a [WalletServer] obtained from a
     * [WalletServerProvider].
     */
    @FlowMethod
    suspend fun authenticate(): AuthenticationFlow

    /**
     * Static information about the available Issuing Authorities.
     *
     * Queried from all issuing authorities at initialization time.
     */
    @FlowMethod
    suspend fun getIssuingAuthorityConfigurations(): List<IssuingAuthorityConfiguration>

    @FlowMethod
    suspend fun getIssuingAuthority(identifier: String): IssuingAuthority

    /**
     * Waits until a notification is available for the client.
     *
     * A wallet should only use this if [WalletServerCapabilities.waitForNotificationSupported] is
     * set to `true`.
     *
     * This may error out if a notification wasn't available within a certain server-defined
     * timeframe.
     *
     * @return a [ByteArray] with the notification payload.
     */
    @FlowMethod
    suspend fun waitForNotification(): ByteArray
}
