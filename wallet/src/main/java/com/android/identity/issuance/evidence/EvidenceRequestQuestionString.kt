package com.android.identity.issuance.evidence

data class EvidenceRequestQuestionString (
    val message: String,
    val defaultValue: String,
    val acceptButtonText: String
) : EvidenceRequest(EvidenceType.QUESTION_STRING)