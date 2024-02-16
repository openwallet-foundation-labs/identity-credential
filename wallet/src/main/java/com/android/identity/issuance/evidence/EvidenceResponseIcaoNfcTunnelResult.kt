package com.android.identity.issuance.evidence

class EvidenceResponseIcaoNfcTunnelResult(
    val advancedAuthenticationType: AdvancedAuthenticationType,
    val dataGroups: Map<Int, ByteArray>,  // data from the passport (DG1-DG15 indexed by 1-15)
    val securityObject: ByteArray  // Card Security Object (SOD)
) : EvidenceResponse(EvidenceType.ICAO_9303_NFC_TUNNEL_RESULT) {
    enum class AdvancedAuthenticationType {
        NONE,
        CHIP,
        ACTIVE
    }
}