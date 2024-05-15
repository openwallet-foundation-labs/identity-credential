package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.crypto.EcPublicKey
import com.android.identity.issuance.CredentialFormat
import kotlinx.datetime.Instant

data class SimpleCredentialRequest(
    val authenticationKey: EcPublicKey,
    val format: CredentialFormat,
    val data: ByteArray,
    val deadline: Instant,
) {

    companion object {
        fun fromCbor(encodedData: ByteArray): SimpleCredentialRequest {
            val map = Cbor.decode(encodedData)
            return SimpleCredentialRequest(
                map["authenticationKey"].asCoseKey.ecPublicKey,
                CredentialFormat.valueOf(map["format"].asTstr),
                map["data"].asBstr,
                Instant.fromEpochMilliseconds(map["deadline"].asNumber)
            )
        }
    }

    fun toCbor(): ByteArray {
        return Cbor.encode(
            CborMap.builder()
                .put("authenticationKey", authenticationKey.toCoseKey().toDataItem)
                .put("format", format.name)
                .put("data", data)
                .put("deadline", deadline.toEpochMilliseconds())
                .end()
                .build())
    }
}