package org.multipaz.issuance.evidence

import kotlinx.io.bytestring.ByteString

/**
 * Message shown after completing the Evidence-gathering steps for Provisioning a document.
 *
 * [messageTitle] title to show above the message
 * [message] message formatted as markdown
 * [assets] images that can be referenced in markdown, type (PNG, JPEG, or SVG) determined by the extension
 * [acceptButtonText] button label to continue to the next screen
 * [rejectButtonText] optional button label to continue without accepting
 */
data class EvidenceRequestCompletionMessage(
    val messageTitle: String,
    val message: String,
    val assets: Map<String, ByteString>,
    val acceptButtonText: String,
    val rejectButtonText: String?,
) : EvidenceRequest()