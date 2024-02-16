package com.android.identity.issuance.evidence

data class EvidenceResponseQuestionMultipleChoice(
    val answerId: String
) : EvidenceResponse(EvidenceType.QUESTION_MULTIPLE_CHOICE)
