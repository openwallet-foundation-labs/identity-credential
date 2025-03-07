package org.multipaz.issuance.evidence

/**
 * Result of reading German eID card with URL content downloaded.
 *
 * This evidence type is created internally by the server from [EvidenceResponseGermanEid]. It cannot
 * be sent from the client, as the server needs to download the content from the given URL directly.
 */
data class EvidenceResponseGermanEidResolved(
    val complete: Boolean,
    val status: String? = null,
    val data: String? = null
) : EvidenceResponse()