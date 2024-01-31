package com.android.identity.issuance

import co.nstant.`in`.cbor.CborBuilder
import com.android.identity.internal.Util

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
            val map = Util.cborDecode(encodedData)
            return CredentialState(
                Util.cborMapExtractNumber(map, "timestamp"),
                CredentialCondition.fromInt(Util.cborMapExtractNumber(map, "condition").toInt()),
                Util.cborMapExtractNumber(map, "numPendingCPO").toInt(),
                Util.cborMapExtractNumber(map, "numAvailableCPO").toInt(),
            )
        }
    }

    fun toCbor(): ByteArray {
        return Util.cborEncode(
            CborBuilder()
                .addMap()
                .put("timestamp", timestamp)
                .put("condition", condition.value.toLong())
                .put("numPendingCPO", numPendingCPO.toLong())
                .put("numAvailableCPO", numAvailableCPO.toLong())
                .end()
                .build().get(0))
    }
}
