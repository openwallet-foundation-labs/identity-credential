package com.android.identity.issuance.evidence

import com.android.identity.cbor.annotation.CborMerge

data class EvidenceResponseIcaoPassiveAuthentication(
    @com.android.identity.cbor.annotation.CborMerge
    val dataGroups: Map<Int, ByteArray>, // data from the passport (DG1-DG15 indexed by 1-15)
    val securityObject: ByteArray, // Card Security Object (SOD)
) : EvidenceResponse() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EvidenceResponseIcaoPassiveAuthentication

        if (dataGroups.size != other.dataGroups.size) return false
        for (entry in dataGroups.entries) {
            if (!entry.value.contentEquals(other.dataGroups[entry.key])) {
                return false
            }
        }
        return securityObject.contentEquals(other.securityObject)
    }

    override fun hashCode(): Int {
        var result = dataGroups.hashCode()
        result = 31 * result + securityObject.contentHashCode()
        return result
    }
}
