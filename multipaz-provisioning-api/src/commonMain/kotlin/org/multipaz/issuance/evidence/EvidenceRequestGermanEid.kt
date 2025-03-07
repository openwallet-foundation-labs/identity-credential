package org.multipaz.issuance.evidence

/**
 * Evidence request type for asking user to identify themselves using German eID card,
 * according to BSI TR-03127.
 */
data class EvidenceRequestGermanEid(
    val tcTokenUrl: String,
    val optionalComponents: List<String>
) : EvidenceRequest()
