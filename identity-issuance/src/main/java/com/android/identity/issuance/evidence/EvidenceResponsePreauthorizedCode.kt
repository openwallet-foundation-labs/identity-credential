package com.android.identity.issuance.evidence

/**
 * Provides OpenId4VCI pre-authorized code to the issuer.
 */
data class EvidenceResponsePreauthorizedCode(
    val code: String,  // preauthorized code
    val txCode: String?  // transaction code entered by the user (may or may not be required)
) : EvidenceResponse()