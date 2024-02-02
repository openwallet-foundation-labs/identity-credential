package com.android.identity.issuance.evidence

data class EvidenceResponseIcaoPassiveAuthentication(
    val dataGroups: Map<Int, ByteArray>,  // data from the passport (DG1-DG15 indexed by 1-15)
    val securityObject: ByteArray  // Card Security Object (SOD)
) : EvidenceResponse(EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EvidenceResponseIcaoPassiveAuthentication

        if (dataGroups != other.dataGroups) return false
        return securityObject.contentEquals(other.securityObject)
    }

    override fun hashCode(): Int {
        var result = dataGroups.hashCode()
        result = 31 * result + securityObject.contentHashCode()
        return result
    }
}

