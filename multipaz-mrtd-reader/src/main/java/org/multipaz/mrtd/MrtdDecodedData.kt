package org.multipaz.mrtd

import kotlinx.io.bytestring.ByteString

/**
 * Data read from the passport or ID card.
 *
 * This data is typically produced from [MrtdNfcData] using [MrtdNfcDataDecoder].
 */
data class MrtdDecodedData(
    val firstName: String,
    val firstNameComponents: List<String>,
    val lastName: String,
    val issuingState: String,
    val nationality: String,
    val gender: String,
    val documentCode: String,
    val documentNumber: String,
    val dateOfBirth: String,  // YYMMDD
    val dateOfExpiry: String,  // YYMMDD
    val personalNumber: String?,
    val optionalData1: String?,
    val optionalData2: String?,
    val photoMediaType: String?,
    val photo: ByteString?,  // JPEG or JPEG2000 bytes
    val signatureMediaType: String?,
    val signature: ByteString? // JPEG or JPEG2000 bytes
)
