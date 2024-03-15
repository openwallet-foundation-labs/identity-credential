package com.android.identity.issuance

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.credential.NameSpacedData

/**
 * The configuration data for a specific issued credential.
 *
 * This is made available by the issuer after identifying and proofing the application and
 * the data in here may contain data specific to the application.
 */
data class CredentialConfiguration(
    /**
     * Display-name for the credential e.g. "Erika's Driving License" or "Utopia Driving Licence"
     */
    val displayName: String,

    /**
     * Card-art for the credential.
     *
     * This should resemble a physical card and be the same aspect ratio (3 3⁄8 in × 2 1⁄8 in,
     * see also ISO/IEC 7810 ID-1).
     */
    val cardArt: ByteArray,

    /**
     * Static data in the credential.
     */
    val staticData: NameSpacedData
) {
    companion object {
        fun fromCbor(encodedData: ByteArray): CredentialConfiguration {
            val map = Cbor.decode(encodedData)
            return CredentialConfiguration(
                map["name"].asTstr,
                map["cardArt"].asBstr,
                NameSpacedData.fromEncodedCbor(map["staticData"].asBstr)
            )
        }

    }

    fun toCbor(): ByteArray {
        return Cbor.encode(
            CborMap.builder()
                .put("name", displayName)
                .put("cardArt", cardArt)
                .put("staticData", staticData.encodeAsCbor())
                .end()
                .build())
    }
}
