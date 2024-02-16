package com.android.identity.issuance.evidence

data class EvidenceRequestQuestionMultipleChoice (
    val message: String,
    val possibleValues: Map<String, String>,  // maps ids to human-readable text
    val acceptButtonText: String
) : EvidenceRequest(EvidenceType.QUESTION_MULTIPLE_CHOICE)