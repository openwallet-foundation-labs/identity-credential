package org.multipaz.issuance.evidence

/**
 * Provides OpenId4VCI credential offer data.
 */
data class EvidenceResponseCredentialOffer(
    val credentialOffer: Openid4VciCredentialOffer
) : EvidenceResponse()