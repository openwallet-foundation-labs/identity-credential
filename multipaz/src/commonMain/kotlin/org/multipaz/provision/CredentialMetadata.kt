package org.multipaz.provision

/**
 * Metadata for a particular type of credential that an issuer can provision.
 */
data class CredentialMetadata(
    /** Credential name and visual representation of that credential in the wallet. */
    val display: Display,
    /** Format of this credential */
    val format: CredentialFormat,
    /** Determines how the key to which the credential is bound should be provided to the issuer */
    val keyProofType: KeyBindingType,
    /** Maximum number of credentials that can be requested in a single request */
    val maxBatchSize: Int
)
