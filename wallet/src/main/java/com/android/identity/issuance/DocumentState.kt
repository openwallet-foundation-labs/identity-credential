package com.android.identity.issuance

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import kotlinx.datetime.Instant

/**
 * The state of a document, as seen from the issuer's point of view.
 */
data class DocumentState(
    /**
     * The point in time this state object was generated.
     */
    val timestamp: Instant,

    /**
     * The current condition of the document.
     */
    val condition: DocumentCondition,

    /**
     * The number of pending credentials.
     *
     * These are credentials which the application requested but which are not yet ready
     * the be collected.
     */
    val numPendingCredentials: Int,

    /**
     * The number of available credentials.
     *
     * These are credentials which the application requested and which are now ready to
     * be collected
     */
    val numAvailableCredentials: Int,
    ) {
    companion object {
        fun fromCbor(encodedData: ByteArray): DocumentState {
            val map = Cbor.decode(encodedData)
            return DocumentState(
                Instant.fromEpochMilliseconds(map["timestamp"].asNumber),
                DocumentCondition.fromInt(map["condition"].asNumber.toInt()),
                map["numPendingCredentials"].asNumber.toInt(),
                map["numAvailableCredentials"].asNumber.toInt()
            )
        }
    }

    fun toCbor(): ByteArray {
        return Cbor.encode(
            CborMap.builder()
                .put("timestamp", timestamp.toEpochMilliseconds())
                .put("condition", condition.value)
                .put("numPendingCredentials", numPendingCredentials)
                .put("numAvailableCredentials", numAvailableCredentials)
                .end()
                .build())
    }
}
