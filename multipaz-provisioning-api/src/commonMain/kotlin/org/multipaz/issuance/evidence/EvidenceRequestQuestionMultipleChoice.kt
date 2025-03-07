package org.multipaz.issuance.evidence

import kotlinx.io.bytestring.ByteString

/**
 * Evidence type for asking a question to the user and collecting an answer from a predefined list.
 *
 * [message] message formatted as markdown
 * [assets] images that can be referenced in markdown, type (PNG, JPEG, or SVG) determined by the extension
 * [possibleValues] maps response ids to human-readable text for all possible response choices
 * [acceptButtonText] text to display on the button to accept a response and continue to the next screen
 */
data class EvidenceRequestQuestionMultipleChoice (
    val message: String,
    val assets: Map<String, ByteString>,
    val possibleValues: Map<String, String>,
    val acceptButtonText: String
) : EvidenceRequest()