package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable

/**
 * Configuration data for the issuing authority.
 *
 * This data is static and available to the application before any documents are provisioned.
 * It can be used to present a menu of available documents to the user.
 */
@CborSerializable
data class IssuingAuthorityConfiguration(
    /**
     * Unique identifier for this object
     */
    val identifier: String,

    /**
     * Display name of the issuing authority suitable, e.g. "Utopia Registry of Identities"
     */
    val issuingAuthorityName: String,

    /**
     * Artwork for the issuing authority.
     *
     * This should be square, e.g. the width and height must be equal.
     */
    val issuingAuthorityLogo: ByteArray,

    /**
     * Description of the document offered by the issuer, e.g. "Utopia Driving License" or
     * "Utopia National Identification Card"
     *
     * This can be used for display in a picker shown to the user. It must include the issuer's
     * name to allow for disambiguation when multiple items are shown.
     */
    val issuingAuthorityDescription: String,

    /**
     * A [DocumentConfiguration] that can be used while proofing is pending for a document.
     */
    val pendingDocumentInformation: DocumentConfiguration,

    /**
     * Recommended number of credentials to request.
     */
    val numberOfCredentialsToRequest: Int?,

    /**
     * Minimum validity in milliseconds
     */
    val minCredentialValidityMillis: Long?,

    /**
     * Maximum number a single credential should be used
     */
    val maxUsesPerCredentials: Int?,
) {
    companion object
}
