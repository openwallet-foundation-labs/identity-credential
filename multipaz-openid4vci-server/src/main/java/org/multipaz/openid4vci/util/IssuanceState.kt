package org.multipaz.openid4vci.util

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.EcPublicKey
import org.multipaz.document.NameSpacedData
import org.multipaz.storage.StorageTableSpec
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.cbor.Cbor
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.models.verifier.Openid4VpVerifierModel
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable
import org.multipaz.storage.KeyExistsStorageException
import org.multipaz.util.toBase64Url

@CborSerializable
data class IssuanceState(
    val clientId: String,
    val scope: String,
    val clientAttestationKey: EcPublicKey?,
    var dpopKey: EcPublicKey?,
    var redirectUri: String?,
    var codeChallenge: ByteString?,
    var clientState: String? = null,
    var dpopNonce: ByteString? = null,
    var openid4VpVerifierModel: Openid4VpVerifierModel? = null,
    var systemOfRecordAuthCode: String? = null,
    var systemOfRecordCodeVerifier: ByteString? = null,
    var systemOfRecordAccess: SystemOfRecordAccess? = null
) {
    companion object {
        private val tableSpec = StorageTableSpec(
            name = "Openid4VciIssuanceSession",
            supportPartitions = false,
            supportExpiration = false
        )

        private val dpopKeyHashTable = StorageTableSpec(
            name = "Openid4VciDPoPKeyHash",
            supportPartitions = false,
            supportExpiration = false
        )

        private fun keyHash(dpopKey: EcPublicKey): String {
            return Crypto.digest(
                algorithm = Algorithm.SHA256,
                message = Cbor.encode(dpopKey.toDataItem())
            ).toBase64Url()
        }

        suspend fun lookupIssuanceStateId(dpopKey: EcPublicKey) =
            BackendEnvironment.getTable(dpopKeyHashTable).get(keyHash(dpopKey))!!.decodeToString()

        suspend fun createIssuanceState(issuanceState: IssuanceState): String {
            val dpopKey = issuanceState.dpopKey
            val existingStateEncodedId = if (dpopKey == null) {
                null
            } else {
                BackendEnvironment.getTable(dpopKeyHashTable).get(keyHash(dpopKey))
            }
            if (existingStateEncodedId != null) {
                val issuanceStateId = existingStateEncodedId.decodeToString()
                BackendEnvironment.getTable(tableSpec).update(
                    key = issuanceStateId,
                    data = ByteString(issuanceState.toCbor())
                )
                return issuanceStateId
            } else {
                val issuanceStateId = BackendEnvironment.getTable(tableSpec).insert(
                    key = null,
                    data = ByteString(issuanceState.toCbor())
                )
                if (dpopKey != null) {
                    updateDPoPKey(issuanceStateId, issuanceState)
                }
                return issuanceStateId
            }
        }

        suspend fun updateDPoPKey(issuanceStateId: String, issuanceState: IssuanceState) {
            val table = BackendEnvironment.getTable(dpopKeyHashTable)
            val key = keyHash(issuanceState.dpopKey!!)
            val data = issuanceStateId.encodeToByteString()
            try {
                table.insert(key = key, data = data)
            } catch (err: KeyExistsStorageException) {
                table.update(key = key, data = data)
            }
        }

        suspend fun updateIssuanceState(issuanceStateId: String, issuanceState: IssuanceState) {
            return BackendEnvironment.getTable(tableSpec).update(
                key = issuanceStateId,
                data = ByteString(issuanceState.toCbor())
            )
        }

        suspend fun getIssuanceState(issuanceStateId: String): IssuanceState {
            val data = BackendEnvironment.getTable(tableSpec).get(issuanceStateId)
                ?: throw IllegalStateException("Unknown or stale issuance session")
            return IssuanceState.fromCbor(data.toByteArray())
        }
    }
}
