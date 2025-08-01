package org.multipaz.rpc.handler

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.DataItem
import org.multipaz.device.AssertionRpcAuth
import org.multipaz.device.DeviceAssertion
import org.multipaz.device.DeviceAssertionException
import org.multipaz.device.DeviceAttestation
import org.multipaz.device.fromCbor
import org.multipaz.device.fromDataItem
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.concurrent.Volatile
import kotlin.experimental.xor
import kotlin.math.min
import kotlin.random.Random
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
    val nonceChecker: suspend (
            clientId: String,
            nonce: ByteString,
            expiration: Instant
        ) -> NonceAndSession = Companion::checkNonce,
    val clientLookup: suspend (clientId: String) -> DeviceAttestation?
            = Companion::getClientDeviceAttestation
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
        val nonceAndSession = nonceChecker(assertion.clientId, assertion.nonce, expiration)
        return RpcAuthContext(
            assertion.clientId,
            nonceAndSession.sessionId,
            nonceAndSession.nextNonce
        )
    }

    data class NonceAndSession(val nextNonce: ByteString, val sessionId: String)

    companion object {
        const val TAG = "RpcAuthInspectorAssertion"

        private val cipherInitLock = Mutex()

        @Volatile
        private var nonceCipher: SimpleCipher? = null

        // Poor man's database transaction. This is not going to be totally safe if multiple
        // processes are using the same database.
        private val nonceTableLock = Mutex()

        val rpcClientTableSpec = StorageTableSpec(
            name = "RpcClientAttestations",
            supportPartitions = false,
            supportExpiration = false
        )

        private val rpcNonceTableSpec = StorageTableSpec(
            name = "RpcAuthAssertionSession",
            supportPartitions = true,
            supportExpiration = true
        )

        private val setupTableSpec = StorageTableSpec(
            name = "RpcAuthAssertionSetup",
            supportPartitions = false,
            supportExpiration = false
        )

        /**
         * [RpcAuthIssuerAssertion] instance that uses defaults for all its parameters.
         */
        val Default = RpcAuthInspectorAssertion()

        private suspend fun checkNonce(
            clientId: String,
            nonce: ByteString,
            expiration: Instant
        ): NonceAndSession {
            val storage = BackendEnvironment.getInterface(Storage::class)!!
            val table = storage.getTable(rpcNonceTableSpec)
            val cipher = getNonceCipher(clientId)
            if (nonce.size == 0) {
                val newNonce = nonceTableLock.withLock {
                    newSession(table, cipher, clientId, expiration)
                }
                throw RpcAuthNonceException(newNonce)
            }
            val sessionId = try {
                cipher.decrypt(nonce.toByteArray()).toBase64Url()
            } catch (err: SimpleCipher.DataTamperedException) {
                // Decryption failed. This is a fake nonce, not merely slate nonce!
                throw RpcAuthException("Invalid nonce", RpcAuthError.FAILED)
            }
            val expectedNonce = table.get(key = sessionId, partitionId = clientId)
            val nextNonce = nonceTableLock.withLock {
                sessionNonce(table, cipher, clientId, sessionId, expiration)
            }
            if (expectedNonce != nonce) {
                throw RpcAuthNonceException(nextNonce)
            }
            return NonceAndSession(
                nextNonce,
                sessionId
            )
        }

        private suspend fun getNonceCipher(clientId: String): SimpleCipher {
            val cipher = nonceCipher
            if (cipher != null) {
                return cipher
            }
            val storage = BackendEnvironment.getInterface(Storage::class)!!
            val table = storage.getTable(setupTableSpec)
            cipherInitLock.withLock {
                if (nonceCipher == null) {
                    var key = table.get("nonceCipherKey")
                    if (key == null) {
                        key = ByteString(Random.nextBytes(16))
                        table.insert("nonceCipherKey", key)
                    }
                    val keyBytes = key.toByteArray()
                    val pad = clientId.encodeToByteArray()
                    for (i in 0..<min(keyBytes.size, pad.size)) {
                        keyBytes[i] = keyBytes[i] xor pad[i]
                    }
                    nonceCipher = AesGcmCipher(keyBytes)
                }
                return nonceCipher!!
            }
        }

        private suspend fun newSession(
            table: StorageTable,
            cipher: SimpleCipher,
            clientId: String,
            expiration: Instant
        ): ByteString {
            val sessionId = table.insert(key = null, partitionId = clientId, data = ByteString())
            Logger.i(TAG, "New session for clientId '$clientId': '$sessionId'")
            val nonce = ByteString(cipher.encrypt(sessionId.fromBase64Url()))
            table.update(
                key = sessionId,
                partitionId = clientId,
                data = nonce,
                expiration = expiration
            )
            return nonce
        }

        private suspend fun sessionNonce(
            table: StorageTable,
            cipher: SimpleCipher,
            clientId: String,
            sessionId: String,
            expiration: Instant
        ): ByteString {
            val nonce = ByteString(cipher.encrypt(sessionId.fromBase64Url()))
            table.delete(key = sessionId, partitionId = clientId)
            table.insert(
                key = sessionId,
                partitionId = clientId,
                data = nonce,
                expiration = expiration
            )
            return nonce
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