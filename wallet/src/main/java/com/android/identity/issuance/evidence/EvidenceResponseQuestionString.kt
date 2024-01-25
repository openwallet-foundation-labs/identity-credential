package com.android.identity.issuance.evidence

data class EvidenceResponseQuestionString(
    val answer: String
) : EvidenceResponse(EvidenceType.QUESTION_STRING)
