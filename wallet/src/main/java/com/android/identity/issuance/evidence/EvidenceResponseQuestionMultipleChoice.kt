package com.android.identity.issuance.evidence

data class EvidenceResponseQuestionMultipleChoice(
    val answer: String
) : EvidenceResponse(EvidenceType.QUESTION_MULTIPLE_CHOICE)
