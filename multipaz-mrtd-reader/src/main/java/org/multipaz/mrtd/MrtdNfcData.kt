package org.multipaz.mrtd

import kotlinx.io.bytestring.ByteString

/**
 * Raw data read from the passport or ID card.
 *
 * This data is typically produced by [MrtdNfcChipAccess]. It is in its raw
 * encoded form as it comes from the card and it is not validated. [MrtdNfcDataDecoder] can
 * validate and decode it.
 */
data class MrtdNfcData(val dataGroups: Map<Int, ByteString>, val sod: ByteString)