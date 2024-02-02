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

    /**
     * Evidence type for the lowest authentication level of an NFC-enabled passport or ID card.
     *
     * See Section 5.1 "Passive Authentication" in ICAO Doc 9303 part 11.
     *
     * See [EvidenceRequestIcaoPassiveAuthentication] and [EvidenceResponseIcaoPassiveAuthentication]
     * for parameters and values.
     */
    ICAO_9303_PASSIVE_AUTHENTICATION,
}
