package com.android.identity.issuance.evidence

data class EvidenceRequestQuestionMultipleChoice (
    val message: String,
    val possibleValues: List<String>,
    val acceptButtonText: String
) : EvidenceRequest(EvidenceType.QUESTION_MULTIPLE_CHOICE)