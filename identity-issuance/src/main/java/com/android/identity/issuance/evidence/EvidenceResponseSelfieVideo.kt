package com.android.identity.issuance.evidence

data class EvidenceResponseSelfieVideo(val video: ByteArray)
    : EvidenceResponse() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EvidenceResponseSelfieVideo

        return video.contentEquals(other.video)
    }

    override fun hashCode(): Int {
        return video.contentHashCode()
    }
}