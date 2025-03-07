package org.multipaz.mrtd

/**
 * Represents a small set of data that is typically captured by OCR from Machine Readable Zone (MRZ)
 * on a passport (or other MRTD).
 *
 * These particular field are important as they provide one way to gain access to the NFC chip in
 * an MRTD. Other info in MRZ (and more) can then be captured in a much better way from the NFC
 * chip.
 */
data class MrtdAccessDataMrz(
    val documentNumber: String,
    val dateOfBirth: String,
    val dateOfExpiration: String
) : MrtdAccessData()