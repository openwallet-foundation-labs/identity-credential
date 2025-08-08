package org.multipaz.provision.openid4vci

import org.multipaz.crypto.Algorithm
import org.multipaz.provision.ProvisioningClient

/**
 * Client set-up for [ProvisioningClient] that support Openid4Vci provisioning.
 */
data class Openid4VciClientPreferences(
    /** OAuth2 client_id parameter */
    val clientId: String,
    /** OAuth2 redirect_url */
    val redirectUrl: String,
    /** List of locales in the order of preference that this client supports */
    val locales: List<String>,
    /** Digital signing algorithms that this client supports */
    val signingAlgorithms: List<Algorithm>
)