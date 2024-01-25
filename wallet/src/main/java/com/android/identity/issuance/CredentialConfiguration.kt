package com.android.identity.issuance

import co.nstant.`in`.cbor.CborBuilder
import com.android.identity.internal.Util

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
     */
    val cardArt: ByteArray,

    // TODO: maybe also include NameSpacedData (for static PII)
) {
    companion object {
        fun fromCbor(encodedData: ByteArray): CredentialConfiguration {
            val map = Util.cborDecode(encodedData)
            return CredentialConfiguration(
                Util.cborMapExtractString(map, "name"),
                Util.cborMapExtractByteString(map, "cardArt"),
            )
        }

    }

    fun toCbor(): ByteArray {
        return Util.cborEncode(
            CborBuilder()
                .addMap()
                .put("name", displayName)
                .put("cardArt", cardArt)
                .end()
                .build().get(0))
    }
}
