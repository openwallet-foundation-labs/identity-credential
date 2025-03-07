package org.multipaz.issuance.evidence

data class EvidenceResponseSelfieVideo(val selfieImage: ByteArray)
    : EvidenceResponse() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EvidenceResponseSelfieVideo) return false

        return selfieImage.contentEquals(other.selfieImage)
    }

    override fun hashCode(): Int {
        return selfieImage.contentHashCode()
    }
}