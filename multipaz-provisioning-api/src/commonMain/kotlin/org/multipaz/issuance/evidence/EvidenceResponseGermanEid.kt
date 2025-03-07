package org.multipaz.issuance.evidence

/**
 * Result of reading German eID card, according to BSI TR-03127.
 */
data class EvidenceResponseGermanEid(
    val complete: Boolean,
    val status: String? = null,
    val url: String? = null
) : EvidenceResponse()
