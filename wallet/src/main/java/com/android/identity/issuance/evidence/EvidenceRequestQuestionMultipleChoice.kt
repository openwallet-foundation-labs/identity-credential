package com.android.identity.issuance.evidence

/**
 * Evidence type for asking a question to the user and collecting an answer from a predefined list.
 */
data class EvidenceRequestQuestionMultipleChoice (
    val message: String,
    val possibleValues: Map<String, String>,  // maps ids to human-readable text
    val acceptButtonText: String
) : EvidenceRequest()