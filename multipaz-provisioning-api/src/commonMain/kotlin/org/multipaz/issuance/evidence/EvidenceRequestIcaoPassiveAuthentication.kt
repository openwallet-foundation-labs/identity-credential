package org.multipaz.issuance.evidence

/**
 * Evidence type for the lowest authentication level of an NFC-enabled passport or ID card.
 *
 * See Section 5.1 "Passive Authentication" in ICAO Doc 9303 part 11.
 */
data class EvidenceRequestIcaoPassiveAuthentication(
  val requestedDataGroups: List<Int>
) : EvidenceRequest()
