package org.multipaz.provisioning.evidence

data class EvidenceResponseWeb(
    /** Portion of the URL used for redirecting (not including EvidenceRequestWeb.redirectUri) */
    val response: String
) : EvidenceResponse()