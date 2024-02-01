package com.android.identity.issuance.evidence

data class EvidenceRequestIcaoPassiveAuthentication(
  val requestedDataGroups: List<Int>
) : EvidenceRequest(EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION)
