package org.multipaz.issuance

import org.multipaz.flow.client.FlowBase
import org.multipaz.flow.annotation.FlowInterface
import org.multipaz.flow.annotation.FlowMethod

@FlowInterface
interface WalletServer: FlowBase {
    /**
     * No need to call on client-side if using a [WalletServer] obtained from a
     * [WalletServerProvider].
     */
    @FlowMethod
    suspend fun authenticate(): AuthenticationFlow

    /**
     * General-purpose server-side application support.
     *
     * This is the only interface supported by the minimal wallet server.
     */
    @FlowMethod
    suspend fun applicationSupport(): ApplicationSupport

    /**
     * Static information about the available Issuing Authorities.
     *
     * Queried from all issuing authorities at initialization time.
     */
    @FlowMethod
    suspend fun getIssuingAuthorityConfigurations(): List<IssuingAuthorityConfiguration>

    /**
     * Obtains interface to a particular Issuing Authority.
     *
     * Do not call this method directly. WalletServerProvider maintains a cache of the issuing
     * authority instances, to avoid creating unneeded instances (that can interfere with
     * notifications), go through WalletServerProvider.
     */
    @FlowMethod
    suspend fun getIssuingAuthority(identifier: String): IssuingAuthority
}
