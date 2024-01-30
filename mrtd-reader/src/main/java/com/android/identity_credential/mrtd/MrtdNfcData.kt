package com.android.identity_credential.mrtd

/**
 * Raw data read from the passport or ID card.
 *
 * This data is typically produced by [MrtdNfcReader] or [MrtdNfcScanner]. It is in its raw
 * encoded form as it comes from the card and it is not validated. [MrtdNfcDataDecoder] can
 * validate and decode it.
 */
data class MrtdNfcData(
    val dg1: ByteArray,
    val dg2: ByteArray,
    val sod: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MrtdNfcData

        if (!dg1.contentEquals(other.dg1)) return false
        if (!dg2.contentEquals(other.dg2)) return false
        return sod.contentEquals(other.sod)
    }

    override fun hashCode(): Int {
        var result = dg1.contentHashCode()
        result = 31 * result + dg2.contentHashCode()
        result = 31 * result + sod.contentHashCode()
        return result
    }
}