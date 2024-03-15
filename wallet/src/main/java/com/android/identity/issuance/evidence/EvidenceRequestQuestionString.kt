package com.android.identity.issuance.evidence

/**
 * Evidence type for asking a question to the user and collecting a textual response.
 */
data class EvidenceRequestQuestionString (
    val message: String,
    val defaultValue: String,
    val acceptButtonText: String
) : EvidenceRequest()