package com.android.identity.mdoc.issuersigned

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Tagged
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import kotlinx.io.bytestring.ByteString

/**
 * A data structure representing `IssuerSignedItem` in ISO/IEC 18013-5:2021.
 */
data class IssuerSignedItem(
    val digestId: Long,
    val random: ByteString,
    val dataElementIdentifier: String,
    val dataElementValue: DataItem
) {

    /**
     * Calculates the digest of `IssuerSignedItemBytes`.
     *
     * @param algorithm the digest algorithm to use, e.g. [Algorithm.SHA256].
     */
    fun calculateDigest(algorithm: Algorithm): ByteString {
        val encodeIssuerSignedItemBytes =
            Cbor.encode(Tagged(Tagged.ENCODED_CBOR, Bstr(Cbor.encode(toDataItem()))))
        return ByteString(Crypto.digest(algorithm, encodeIssuerSignedItemBytes))
    }

    /**
     * Generate `IssuerSignedItem` CBOR.
     *
     * @return a [DataItem] for `IssuerSignedItem` CBOR.
     */
    fun toDataItem(): DataItem {
        return CborMap.builder()
            .put("digestID", digestId)
            .put("random", random.toByteArray())
            .put("elementIdentifier", dataElementIdentifier)
            .put("elementValue", dataElementValue)
            .end()
            .build()
    }
    
    companion object {
        /**
         * Parse `IssuerSignedItem` CBOR.
         *
         * @param issuerSignedItem a [DataItem] for `IssuerSignedItem` CBOR.
         * @return the parsed representation.
         */
        fun fromDataItem(issuerSignedItem: DataItem): IssuerSignedItem {
            return IssuerSignedItem(
                digestId = issuerSignedItem["digestID"].asNumber,
                random = ByteString(issuerSignedItem["random"].asBstr),
                dataElementIdentifier = issuerSignedItem["elementIdentifier"].asTstr,
                dataElementValue = issuerSignedItem["elementValue"]
            )
        }
    }
}
