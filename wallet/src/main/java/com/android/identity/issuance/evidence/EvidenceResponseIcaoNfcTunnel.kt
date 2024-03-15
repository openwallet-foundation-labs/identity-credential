package com.android.identity.issuance.evidence

/**
 * Response for [EvidenceRequestIcaoNfcTunnel]
 *
 * TODO: define the response to handshake message; in particular we may need to send PACE
 * key to support Terminal Authentication. For now handshake message is empty.
 */
data class EvidenceResponseIcaoNfcTunnel(val response: ByteArray)
    : EvidenceResponse() {

    override fun toString(): String {
        return "EvidenceResponseIcaoNfcTunnel{length=${response.size}}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EvidenceResponseIcaoNfcTunnel

        return response.contentEquals(other.response)
    }

    override fun hashCode(): Int {
        return response.contentHashCode()
    }
}