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

    /**
     * Evidence type for extracting data from an NFC-enabled passport or ID card through a
     * tunneled NFC connection.
     *
     * This is necessary for Chip Authentication and Terminal Authentication, but can be used for
     * any open-ended reading of data from a passport/MRTD.
     *
     * See Section 6.2 "Chip Authentication" and Section 7.1 "Terminal Authentication" in
     * ICAO Doc 9303 part 11.
     */
    ICAO_9303_NFC_TUNNEL,

    /**
     * Evidence type for that represents the result of communicating through the tunnel
     * implemented by [ICAO_9303_NFC_TUNNEL] requests and responses.
     *
     * This cannot be sent directly, it is only created as the result of tunnel communication.
     */
    ICAO_9303_NFC_TUNNEL_RESULT,
}
