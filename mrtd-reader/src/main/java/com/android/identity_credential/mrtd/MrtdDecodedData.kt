package com.android.identity_credential.mrtd

import android.graphics.Bitmap

/**
 * Data read from the passport or ID card.
 *
 * This data is typically produced from [MrtdNfcData] using [MrtdNfcDataDecoder].
 */
data class MrtdDecodedData(
    val firstName: String,
    val lastName: String,
    val state: String,
    val nationality: String,
    val gender: String,
    val photo: Bitmap?,
    val signature: Bitmap?
)
