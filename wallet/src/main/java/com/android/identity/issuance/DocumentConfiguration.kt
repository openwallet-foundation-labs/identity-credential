package com.android.identity.issuance

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.Simple
import com.android.identity.document.NameSpacedData

/**
 * The configuration data for a specific issued credential.
 *
 * This is made available by the issuer after identifying and proofing the application and
 * the data in here may contain data specific to the application.
 */
data class DocumentConfiguration(
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

    // TODO: we need to move mdocDocType and staticData to a per-credential-format map

    /**
     * The mdoc DocType for the credential.
     */
    val mdocDocType: String,

    /**
     * Static data in the credential.
     */
    val staticData: NameSpacedData,

    /**
     * If `true`, require that the user authenticates to view document information.
     *
     * The authentication required will be the same as from one of the credentials
     * in the document e.g. LSKF/Biometric or passphrase.
     */
    val requireUserAuthenticationToViewDocument: Boolean,
) {
    companion object {
        fun fromCbor(encodedData: ByteArray): DocumentConfiguration {
            val map = Cbor.decode(encodedData)
            return DocumentConfiguration(
                map["name"].asTstr,
                map["cardArt"].asBstr,
                map["mdocDocType"].asTstr,
                NameSpacedData.fromEncodedCbor(map["staticData"].asBstr),
                map.getOrDefault("requireUserAuthenticationToViewDocument", Simple.FALSE).asBoolean
            )
        }

    }

    fun toCbor(): ByteArray {
        return Cbor.encode(
            CborMap.builder()
                .put("name", displayName)
                .put("cardArt", cardArt)
                .put("mdocDocType", mdocDocType)
                .put("staticData", staticData.encodeAsCbor())
                .put("requireUserAuthenticationToViewDocument", requireUserAuthenticationToViewDocument)
                .end()
                .build())
    }
}
