package org.multipaz.legacyprovisioning

import org.multipaz.rpc.annotation.RpcInterface
import org.multipaz.rpc.annotation.RpcMethod

@RpcInterface
interface ProvisioningBackend {
    /**
     * General-purpose server-side application support.
     *
     * This is the only interface supported by the minimal wallet server.
     */
    @RpcMethod
    suspend fun applicationSupport(): ApplicationSupport

    /**
     * Static information about the available Issuing Authorities.
     *
     * Queried from all issuing authorities at initialization time.
     */
    @RpcMethod
    suspend fun getIssuingAuthorityConfigurations(): List<IssuingAuthorityConfiguration>

    /**
     * Obtains interface to a particular Issuing Authority.
     *
     * Do not call this method directly. WalletServerProvider maintains a cache of the issuing
     * authority instances, to avoid creating unneeded instances (that can interfere with
     * notifications), go through WalletServerProvider.
     */
    @RpcMethod
    suspend fun getIssuingAuthority(identifier: String): IssuingAuthority
}
