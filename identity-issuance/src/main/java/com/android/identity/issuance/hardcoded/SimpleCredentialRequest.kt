package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.crypto.EcPublicKey
import com.android.identity.issuance.CredentialFormat
import kotlinx.io.bytestring.ByteString

data class SimpleCredentialRequest(
    val authenticationKey: EcPublicKey,
    val format: CredentialFormat,
    val data: ByteString,
) {

    companion object {
        fun fromCbor(encodedData: ByteString): SimpleCredentialRequest {
            val map = Cbor.decode(encodedData)
            return SimpleCredentialRequest(
                map["authenticationKey"].asCoseKey.ecPublicKey,
                CredentialFormat.valueOf(map["format"].asTstr),
                map["data"].asBstr,
            )
        }
    }

    fun toCbor(): ByteString {
        return Cbor.encode(
            CborMap.builder()
                .put("authenticationKey", authenticationKey.toCoseKey().toDataItem())
                .put("format", format.name)
                .put("data", data)
                .end()
                .build())
    }
}