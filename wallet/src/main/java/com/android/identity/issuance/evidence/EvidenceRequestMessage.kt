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
) : EvidenceRequest() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EvidenceRequestMessage

        if (message != other.message) return false
        if (assets.size != other.assets.size) return false
        for (entry in assets.entries) {
            if (!entry.value.contentEquals(other.assets[entry.key])) {
                return false
            }
        }
        if (acceptButtonText != other.acceptButtonText) return false
        return rejectButtonText == other.rejectButtonText
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + assets.hashCode()
        result = 31 * result + acceptButtonText.hashCode()
        result = 31 * result + (rejectButtonText?.hashCode() ?: 0)
        return result
    }
}
