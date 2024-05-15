package com.android.identity.issuance.evidence

import com.android.identity.cbor.annotation.CborMerge

/**
 * Evidence type for that represents the result of communicating through the tunnel
 * implemented by [EvidenceRequestIcaoNfcTunnel] requests and responses.
 *
 * This cannot be sent directly, it is only created as the result of tunnel communication.
 */
data class EvidenceResponseIcaoNfcTunnelResult(
    val advancedAuthenticationType: AdvancedAuthenticationType,
    @com.android.identity.cbor.annotation.CborMerge
    val dataGroups: Map<Int, ByteArray>,  // data from the passport (DG1-DG15 indexed by 1-15)
    val securityObject: ByteArray  // Card Security Object (SOD)
) : EvidenceResponse() {

    enum class AdvancedAuthenticationType {
        NONE,
        CHIP,
        ACTIVE
    }

    override fun toString(): String {
        return "EvidenceResponseIcaoNfcTunnelResult($advancedAuthenticationType, dataGroups=${dataGroups.keys}, securityObject.len=${securityObject.size})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EvidenceResponseIcaoNfcTunnelResult

        if (advancedAuthenticationType != other.advancedAuthenticationType) return false
        if (dataGroups.size != other.dataGroups.size) return false
        for (entry in dataGroups.entries) {
            if (!entry.value.contentEquals(other.dataGroups[entry.key])) {
                return false
            }
        }

        return securityObject.contentEquals(other.securityObject)
    }

    override fun hashCode(): Int {
        var result = advancedAuthenticationType.hashCode()
        result = 31 * result + dataGroups.hashCode()
        result = 31 * result + securityObject.contentHashCode()
        return result
    }
}