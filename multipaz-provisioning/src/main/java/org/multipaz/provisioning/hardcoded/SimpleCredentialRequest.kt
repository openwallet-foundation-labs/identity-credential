package org.multipaz.provisioning.hardcoded

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.buildCborMap
import org.multipaz.crypto.EcPublicKey
import org.multipaz.provisioning.CredentialFormat

data class SimpleCredentialRequest(
    val authenticationKey: EcPublicKey,
    val format: CredentialFormat,
    val data: ByteArray,
) {

    companion object {
        fun fromCbor(encodedData: ByteArray): SimpleCredentialRequest {
            val map = Cbor.decode(encodedData)
            return SimpleCredentialRequest(
                map["authenticationKey"].asCoseKey.ecPublicKey,
                CredentialFormat.valueOf(map["format"].asTstr),
                map["data"].asBstr,
            )
        }
    }

    fun toCbor(): ByteArray {
        return Cbor.encode(
            buildCborMap {
                put("authenticationKey", authenticationKey.toCoseKey().toDataItem())
                put("format", format.name)
                put("data", data)
            }
        )
    }
}