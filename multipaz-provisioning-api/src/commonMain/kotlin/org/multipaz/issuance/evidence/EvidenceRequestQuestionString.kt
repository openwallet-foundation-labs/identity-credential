package org.multipaz.issuance.evidence

import kotlinx.io.bytestring.ByteString

/**
 * Evidence type for asking a question to the user and collecting a textual response.
 *
 * [message] message formatted as markdown
 * [assets] images that can be referenced in markdown, type (PNG, JPEG, or SVG) determined by the extension
 * [defaultValue] default (prefilled) value for the answer
 * [acceptButtonText] text to display on the button to accept a response and continue to the next screen
 */
data class EvidenceRequestQuestionString (
    val message: String,
    val assets: Map<String, ByteString>,
    val defaultValue: String,
    val acceptButtonText: String
) : EvidenceRequest()