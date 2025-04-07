package org.multipaz.rpc.handler

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.buildByteString
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.DataItem
import org.multipaz.device.AssertionRpcAuth
import org.multipaz.device.DeviceAssertion
import org.multipaz.device.DeviceAssertionException
import org.multipaz.device.DeviceAttestation
import org.multipaz.device.fromCbor
import org.multipaz.device.fromDataItem
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.storage.KeyExistsStorageException
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTableSpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Implementation of [RpcAuthInspector] that requires each RPC call to be authorized with
 * [AssertionRpcAuth] object signed by a secure device key (see [DeviceAssertion]). Authorization
 * is only trusted by [timeout] duration. Nonce [AssertionRpcAuth.nonce] uniqueness is checked
 * by [nonceChecker] and [DeviceAttestation] that is used to validate [AssertionRpcAuth] is looked
 * up by the client id using [clientLookup].
 */
class RpcAuthInspectorAssertion(
    val timeout: Duration = 10.minutes,
    val nonceChecker: suspend (clientId: String, nonce: String, expiration: Instant) -> Unit
            = ::checkNonceForReplay,
    val clientLookup: suspend (clientId: String) -> DeviceAttestation?
            = ::getClientDeviceAttestation
): RpcAuthInspector {
    override suspend fun authCheck(
        target: String,
        method: String,
        payload: Bstr,
        authMessage: DataItem
    ): RpcAuthContext {
        val deviceAssertion = DeviceAssertion.fromDataItem(authMessage["assertion"])
        val assertion = deviceAssertion.assertion as AssertionRpcAuth
        val attestation = clientLookup(assertion.clientId)
            ?: throw RpcAuthException(
                message = "Client '${assertion.clientId}' is unknown",
                rpcAuthError = RpcAuthError.UNKNOWN_CLIENT_ID
            )
        if (assertion.target != target || assertion.method != method) {
            throw RpcAuthException(
                message = "RPC message is directed to a wrong target or method",
                rpcAuthError = RpcAuthError.REQUEST_URL_MISMATCH
            )
        }
        val expiration = assertion.timestamp + timeout
        if (expiration <= Clock.System.now()) {
            throw RpcAuthException(
                message = "Message is expired",
                rpcAuthError = RpcAuthError.STALE
            )
        }
        try {
            attestation.validateAssertion(deviceAssertion)
        } catch (err: DeviceAssertionException) {
            throw RpcAuthException(
                message = "Assertion validation: ${err.message}",
                rpcAuthError = RpcAuthError.FAILED
            )
        }
        nonceChecker(assertion.clientId, assertion.nonce, expiration)
        return RpcAuthContext(assertion.clientId)
    }

    companion object {
        val rpcClientTableSpec = StorageTableSpec(
            name = "RpcClientAttestations",
            supportPartitions = false,
            supportExpiration = false
        )

        val rpcNonceTableSpec = StorageTableSpec(
            name = "RpcClientNonce",
            supportPartitions = true,
            supportExpiration = true
        )

        /**
         * [RpcAuthIssuerAssertion] instance that uses defaults for all its parameters.
         */
        val Default = RpcAuthInspectorAssertion()

        private suspend fun checkNonceForReplay(
            clientId: String,
            nonce: String,
            expiration: Instant
        ) {
            val storage = BackendEnvironment.getInterface(Storage::class)!!
            val table = storage.getTable(rpcNonceTableSpec)
            try {
                table.insert(
                    key = nonce,
                    partitionId = clientId,
                    expiration = expiration,
                    data = buildByteString { }
                )
            } catch (err: KeyExistsStorageException) {
                throw RpcAuthException(
                    message = "Nonce reuse detected: ${err.message}",
                    rpcAuthError = RpcAuthError.REPLAY
                )
            }
        }

        suspend fun getClientDeviceAttestation(
            clientId: String
        ): DeviceAttestation? {
            val storage = BackendEnvironment.getInterface(Storage::class)!!
            val table = storage.getTable(rpcClientTableSpec)
            val record = table.get(key = clientId) ?: return null
            return DeviceAttestation.fromCbor(record.toByteArray())
        }
    }
}