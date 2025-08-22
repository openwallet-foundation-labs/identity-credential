package org.multipaz.provisioning

/**
 * Credential issuer metadata.
 */
data class ProvisioningMetadata(
    /** Issuer name and logo.*/
    val display: Display,
    /**
     * Credentials that could be provisioned from the issuer indexed by credential configuration
     * id.
     *
     * When [ProvisioningClient] is configured to issue a particular kind of credential, only
     * that credential will be present in the map.
     */
    val credentials: Map<String, CredentialMetadata>
)