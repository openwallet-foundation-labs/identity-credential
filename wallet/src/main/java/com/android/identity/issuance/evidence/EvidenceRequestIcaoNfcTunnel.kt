package com.android.identity.issuance.evidence

class EvidenceRequestIcaoNfcTunnel(
    val requestType: EvidenceRequestIcaoNfcTunnelType,
    val progressPercent: Int,
    val message: ByteArray)
    : EvidenceRequest(EvidenceType.ICAO_9303_NFC_TUNNEL)