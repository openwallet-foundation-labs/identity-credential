package org.multipaz.provisioning.openid4vci

import org.multipaz.provisioning.ProvisioningClient

object OpenID4VCI {
    /**
     * Creates [ProvisioningClient] for Openid4Vci from a credential offer.
     */
    suspend fun createClientFromOffer(
        offerUri: String,
        clientPreferences: OpenID4VCIClientPreferences
    ): ProvisioningClient = OpenID4VCIProvisioningClient.createFromOffer(offerUri, clientPreferences)
}