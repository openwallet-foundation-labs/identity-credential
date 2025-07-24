package org.multipaz.provision.openid4vci

import org.multipaz.provision.ProvisioningClient

object OpenID4VCI {
    /**
     * Creates [ProvisioningClient] for Openid4Vci from a credential offer.
     */
    suspend fun createClientFromOffer(
        offerUri: String,
        clientPreferences: OpenID4VCIClientPreferences
    ): ProvisioningClient = OpenID4VCIProvisioningClient.createFromOffer(offerUri, clientPreferences)
}