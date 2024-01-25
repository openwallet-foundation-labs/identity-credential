package com.android.identity.issuance

/**
 * Configuration data for the issuing authority.
 *
 * This data is static and available to the application before any credentials are provisioned.
 * It can be used to present a menu of available credentials to the user.
 */
data class IssuingAuthorityConfiguration(
    /**
     * Unique identifier for the Issuing Authority
     */
    val identifier: String,

    /**
     * Display name suitable for Issuing Authority-picker.
     */
    val name: String,

    /**
     * Icon suitable for displaying in an IA-picker. Value is a encoded bitmap.
     */
    val icon: ByteArray,

    /**
     * The credential presentation formats available.
     */
    val credentialFormats: Set<CredentialPresentationFormat>,

    /**
     * A [CredentialConfiguration] that can be used while proofing is pending for a credential.
     */
    val pendingCredentialInformation: CredentialConfiguration
)
