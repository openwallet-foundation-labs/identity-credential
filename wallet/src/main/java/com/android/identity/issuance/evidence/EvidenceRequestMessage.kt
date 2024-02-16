package com.android.identity.issuance.evidence

data class EvidenceRequestMessage(
    val message: String,
    val acceptButtonText: String,
    val rejectButtonText: String?,
) : EvidenceRequest(EvidenceType.MESSAGE)