package com.android.identity.issuance

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap

/**
 * The state of a credential, as seen from the issuer's point of view.
 */
data class CredentialState(
    /**
     * The point in time this state object was generated.
     */
    val timestamp: Long,

    /**
     * The current condition of the credential.
     */
    val condition: CredentialCondition,

    /**
     * The number of pending Credential Presentation Objects.
     *
     * These are CPOs which the application requested but which are not yet ready
     * the be collected.
     */
    val numPendingCPO: Int,

    /**
     * The number of available Credential Presentation Objects.
     *
     * These are CPOs which the application requested and which are now ready to
     * be collected
     */
    val numAvailableCPO: Int,
    ) {
    companion object {
        fun fromCbor(encodedData: ByteArray): CredentialState {
            val map = Cbor.decode(encodedData)
            return CredentialState(
                map["timestamp"].asNumber,
                CredentialCondition.fromInt(map["condition"].asNumber.toInt()),
                map["numPendingCPO"].asNumber.toInt(),
                map["numAvailableCPO"].asNumber.toInt()
            )
        }
    }

    fun toCbor(): ByteArray {
        return Cbor.encode(
            CborMap.builder()
                .put("timestamp", timestamp)
                .put("condition", condition.value)
                .put("numPendingCPO", numPendingCPO)
                .put("numAvailableCPO", numAvailableCPO)
                .end()
                .build())
    }
}
