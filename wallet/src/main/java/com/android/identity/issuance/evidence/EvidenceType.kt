package com.android.identity.issuance.evidence


/**
 * Evidence types.
 */
enum class EvidenceType {

    /**
     * Evidence type displaying a message to the user.
     *
     * See [EvidenceRequestMessage] and [EvidenceResponseMessage] for parameters and values.
     */
    MESSAGE,

    /**
     * Evidence type for asking a question to the user and collecting a textual response.
     *
     * See [EvidenceRequestQuestionString] and [EvidenceResponseQuestionString] for parameters and values.
     */
    QUESTION_STRING,

    /**
     * Evidence type for asking a question to the user and collecting an answer from a predefined list.
     *
     * See [EvidenceRequestQuestionMultipleChoice] and [EvidenceResponseQuestionMultipleChoice] for parameters and values.
     */
    QUESTION_MULTIPLE_CHOICE,
}
