package org.multipaz.legacyprovisioning.evidence

/**
 * Launch a browser using the given URL.
 */
data class EvidenceRequestWeb(
    val url: String,
    val redirectUri: String,
) : EvidenceRequest()