package com.android.identity.issuance.evidence

/**
 * Rich-text message presented to the user.
 *
 * [message] message formatted as markdown
 * [assets] images that can be referenced in markdown, type (PNG, JPEG, or SVG) determined by the extension
 * [acceptButtonText] button label to continue to the next screen
 * [rejectButtonText] optional button label to continue without accepting
 */
data class EvidenceRequestMessage(
    val message: String,
    val assets: Map<String, ByteArray>,
    val acceptButtonText: String,
    val rejectButtonText: String?,
) : EvidenceRequest(EvidenceType.MESSAGE)