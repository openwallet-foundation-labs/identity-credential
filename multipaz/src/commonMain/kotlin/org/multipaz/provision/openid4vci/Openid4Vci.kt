package org.multipaz.provision.openid4vci

import org.multipaz.provision.ProvisioningClient

object Openid4Vci {
    /**
     * Creates [ProvisioningClient] for Openid4Vci from a credential offer.
     */
    suspend fun createClientFromOffer(
        offerUri: String,
        clientPreferences: Openid4VciClientPreferences
    ): ProvisioningClient = Openid4VciProvisioningClient.createFromOffer(offerUri, clientPreferences)
}