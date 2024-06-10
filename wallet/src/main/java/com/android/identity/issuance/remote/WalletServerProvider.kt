package com.android.identity.issuance.remote

import android.content.Context
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.DataItem
import com.android.identity.crypto.Algorithm
import com.android.identity.flow.handler.FlowHandler
import com.android.identity.flow.handler.FlowHandlerLocal
import com.android.identity.flow.handler.FlowHandlerRemote
import com.android.identity.issuance.ClientAuthentication
import com.android.identity.issuance.ClientChallenge
import com.android.identity.issuance.WalletApplicationCapabilities
import com.android.identity.issuance.WalletNotificationPayload
import com.android.identity.issuance.WalletServer
import com.android.identity.issuance.WalletServerImpl
import com.android.identity.issuance.WalletServerCapabilities
import com.android.identity.issuance.authenticationMessage
import com.android.identity.issuance.extractAttestationSequence
import com.android.identity.issuance.fromCbor
import com.android.identity.issuance.hardcoded.WalletServerState
import com.android.identity.issuance.toCbor
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.SecureArea
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.SettingsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1OctetString
import kotlin.time.Duration.Companion.seconds


/**
 * An object used to connect to a remote wallet server.
 */
class WalletServerProvider(
    private val context: Context,
    private val secureArea: SecureArea,
    private val settingsModel: SettingsModel,
    private val storageEngine: StorageEngine,
    private val getWalletApplicationCapabilities: suspend () -> WalletApplicationCapabilities
) {
    private var instance: WalletServer? = null
    private val instanceLock = Mutex()

    private var _walletServerCapabilities: WalletServerCapabilities? = null

    private var notificationsJob: Job? = null

    /**
     * Information about the wallet server that the application is connected to.
     *
     * @throws IllegalStateException if called if we have never previously communicated
     * with the wallet-server
     */
    val walletServerCapabilities: WalletServerCapabilities
        get() {
            if (_walletServerCapabilities == null) {
                throw IllegalStateException("Don't call before getWalletServer()")
            }
            return _walletServerCapabilities!!
        }

    companion object {
        private const val TAG = "WalletServerProvider"

        // Used only for SimpleNotifications
        private const val RECONNECT_DELAY_INITIAL_SECONDS = 1
        private const val RECONNECT_DELAY_MAX_SECONDS = 30

        private val noopCipher = object : FlowHandlerLocal.SimpleCipher {
            override fun encrypt(plaintext: ByteArray): ByteArray {
                return plaintext
            }

            override fun decrypt(ciphertext: ByteArray): ByteArray {
                return ciphertext
            }
        }
    }

    private val baseUrl: String
        get() = settingsModel.walletServerUrl.value!!

    init {
        _walletServerCapabilities = storageEngine.get("WalletServerCapabilities")?.let {
            WalletServerCapabilities.fromCbor(it)
        }
        settingsModel.walletServerUrl.observeForever {
            runBlocking {
                instanceLock.withLock {
                    notificationsJob?.cancel()
                    notificationsJob = null
                    instance = null
                }
            }
        }
    }

    private fun createFlowHandler(): FlowHandler {
        return if (baseUrl == "dev:") {
            val flowHandlerLocal = FlowHandlerLocal.Builder()
            WalletServerState.registerAll(flowHandlerLocal)
            WrapperFlowHandler(flowHandlerLocal.build(noopCipher,
                LocalDevelopmentEnvironment(context, this)))
        } else {
            val httpClient = WalletHttpClient(baseUrl)
            FlowHandlerRemote(httpClient)
        }
    }

    /**
     * Connects to the remote wallet server.
     *
     * This process includes running through the authentication flow to prove to the remote wallet
     * server that the client is in good standing, e.g. that Verified Boot is GREEN, Android patch
     * level is sufficiently fresh, the app signature keys are as expected, and so on.
     *
     * This is usually only called for operations where the wallet actively needs to interact
     * with the wallet server, e.g. when adding a new document or refreshing state. When the
     * call succeeds, the resulting instance is cached and returned immediately in future calls.
     *
     * @return A [WalletServer] which can be used to interact with the remote wallet server.
     * @throws FlowHandlerRemote.ConnectionException if unable to connect.
     */
    suspend fun getWalletServer(): WalletServer {
        instanceLock.withLock {
            if (instance == null) {
                instance = getWalletServerUnlocked()
                Logger.i(TAG, "Created new WalletServer instance (URL $baseUrl)")
            } else {
                Logger.i(TAG, "Reusing existing WalletServer instance (URL $baseUrl)")
            }
            return instance!!
        }
    }

    private suspend fun getWalletServerUnlocked(): WalletServer {
        // "root" is the entry point for the server, see FlowState annotation on
        // com.android.identity.issuance.hardcoded.WalletServerState
        val walletServer = WalletServerImpl(createFlowHandler(), "root")
        val alias = "ClientKey:$baseUrl"
        var keyInfo: KeyInfo? = null
        var challenge: ClientChallenge? = null
        val authentication = walletServer.authenticate()
        try {
            keyInfo = secureArea.getKeyInfo(alias)
        } catch (ex: Exception) {
            challenge = authentication.requestChallenge("")
        }
        if (keyInfo != null) {
            val seq = extractAttestationSequence(keyInfo.attestation)
            val clientId = String(ASN1OctetString.getInstance(seq.getObjectAt(4)).octets)
            challenge = authentication.requestChallenge(clientId)
            if (clientId != challenge.clientId) {
                secureArea.deleteKey(alias)
                keyInfo = null
            }
        }
        val newClient = keyInfo == null
        if (newClient) {
            secureArea.createKey(alias, CreateKeySettings(challenge!!.clientId.toByteArray()))
            keyInfo = secureArea.getKeyInfo(alias)
        }
        val message = authenticationMessage(challenge!!.clientId, challenge.nonce)
        _walletServerCapabilities = authentication.authenticate(ClientAuthentication(
            ByteString(secureArea.sign(alias, Algorithm.ES256, message.toByteArray(), null)),
            if (newClient) keyInfo!!.attestation else null,
            getWalletApplicationCapabilities()
        ))
        authentication.complete()
        storageEngine.put("WalletServerCapabilities", _walletServerCapabilities!!.toCbor())

        if (walletServerCapabilities.waitForNotificationSupported) {
            // Listen for notifications in a separate coroutine and do bounded exponential backoff
            // when trying to reconnect and the server isn't reachable.
            var currentReconnectDelay = RECONNECT_DELAY_INITIAL_SECONDS.seconds
            notificationsJob = CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    try {
                        Logger.i(TAG, "Listening for notifications via waitForNotification()")
                        val payload = walletServer.waitForNotification()
                        // Reset delay
                        currentReconnectDelay = RECONNECT_DELAY_INITIAL_SECONDS.seconds
                        Logger.i(TAG, "Received notification ${Cbor.toDiagnostics(payload)}")
                        val data = WalletNotificationPayload.fromCbor(payload)
                        CoroutineScope(Dispatchers.IO).launch {
                            _eventFlow.emit(Pair(data.issuingAuthorityId, data.documentId))
                        }
                    } catch (e: FlowHandlerRemote.ConnectionRefusedException) {
                        Logger.w(TAG, "Error connecting to the wallet server for notifications", e)
                        delay(currentReconnectDelay)
                        Logger.w(TAG, "Waited $currentReconnectDelay, reconnecting")
                        currentReconnectDelay *= 2
                        if (currentReconnectDelay > RECONNECT_DELAY_MAX_SECONDS.seconds) {
                            currentReconnectDelay = RECONNECT_DELAY_MAX_SECONDS.seconds
                        }
                    } catch (e: FlowHandlerRemote.TimeoutException) {
                        Logger.d(TAG, "Timeout while waiting for notification", e)
                        delay(5.seconds)
                    } catch (e: FlowHandlerRemote.RemoteException) {
                        // Note: This is the expected path b/c the server's timeout is
                        // set to 3 minutes (see WalletServerState.waitForNotification())
                        // and the client timeout is 5 minutes (see WalletHttpClient)
                        //
                        Logger.d(TAG, "Reached server timeout for notifications. Pinging again.")
                    } catch (e: Throwable) {
                        Logger.w(TAG, "Error while waiting for notification", e)
                        delay(5.seconds)
                    }
                }
            }
        }

        return walletServer
    }

    /**
     * A [SharedFlow] which can be used to listen for when a document has changed state
     * on the issuer side.
     *
     * The first parameter in the pair is `issuingAuthorityIdentifier`, the second parameter
     * is `documentIdentifier`.
     */
    val eventFlow : SharedFlow<Pair<String, String>>
        get() = _eventFlow.asSharedFlow()

    internal val _eventFlow = MutableSharedFlow<Pair<String, String>>()

    /**
     * Flow handler that delegates to a base, ensuring that the work is done in IO dispatchers
     * and logging the calls.
     */
    internal class WrapperFlowHandler(private val base: FlowHandler): FlowHandler {
        companion object {
            const val TAG = "FlowRpc"
        }

        override suspend fun get(flow: String, method: String): DataItem {
            Logger.i(TAG, "GET [${Thread.currentThread().name}] $flow/$method")
            val start = System.nanoTime()
            try {
                return withContext(Dispatchers.IO) {
                    base.get(flow, method)
                }
            } finally {
                val duration = System.nanoTime() - start
                Logger.i(TAG, "${durationText(duration)}[${Thread.currentThread().name}] $flow/$method")
            }
        }

        override suspend fun post(flow: String, method: String, args: List<DataItem>): List<DataItem> {
            Logger.i(TAG, "POST [${Thread.currentThread().name}] $flow/$method")
            val start = System.nanoTime()
            try {
                return withContext(Dispatchers.IO) {
                    base.post(flow, method, args)
                }
            } finally {
                val duration = System.nanoTime() - start
                Logger.i(TAG, "${durationText(duration)} [${Thread.currentThread().name}] $flow/$method")
            }
        }

        private fun durationText(durationNano: Long): String {
            return (durationNano/1000000).toString().padEnd(4, ' ')
        }
    }
}