package com.android.identity.issuance.evidence

import kotlinx.io.bytestring.ByteString

data class EvidenceResponseSelfieVideo(val selfieImage: ByteString)
    : EvidenceResponse() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EvidenceResponseSelfieVideo) return false

        return selfieImage == other.selfieImage
    }

    override fun hashCode(): Int {
        return selfieImage.hashCode()
    }
}