package com.android.identity.issuance.remote

import android.content.Context
import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.cbor.Bstr
import com.android.identity.cbor.DataItem
import com.android.identity.crypto.Algorithm
import com.android.identity.flow.handler.FlowDispatcher
import com.android.identity.flow.handler.FlowDispatcherHttp
import com.android.identity.flow.handler.FlowDispatcherLocal
import com.android.identity.flow.handler.FlowExceptionMap
import com.android.identity.flow.handler.FlowNotificationsLocal
import com.android.identity.flow.handler.FlowNotifier
import com.android.identity.flow.handler.FlowNotifierPoll
import com.android.identity.flow.handler.FlowPollHttp
import com.android.identity.flow.handler.SimpleCipher
import com.android.identity.flow.transport.HttpTransport
import com.android.identity.issuance.ClientAuthentication
import com.android.identity.issuance.ClientChallenge
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.WalletApplicationCapabilities
import com.android.identity.issuance.WalletServer
import com.android.identity.issuance.WalletServerImpl
import com.android.identity.issuance.WalletServerCapabilities
import com.android.identity.issuance.authenticationMessage
import com.android.identity.issuance.extractAttestationSequence
import com.android.identity.issuance.fromCbor
import com.android.identity.issuance.wallet.WalletServerState
import com.android.identity.issuance.toCbor
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.SecureArea
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.SettingsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.ASN1OctetString
import kotlin.time.Duration.Companion.seconds


/**
 * An object used to connect to a remote wallet server.
 */
class WalletServerProvider(
    private val context: Context,
    private val secureArea: AndroidKeystoreSecureArea,
    private val settingsModel: SettingsModel,
    private val storageEngine: StorageEngine,
    private val getWalletApplicationCapabilities: suspend () -> WalletApplicationCapabilities
) {
    private val instanceLock = Mutex()
    private var instance: WalletServer? = null
    private val issuingAuthorityMap = mutableMapOf<String, IssuingAuthority>()

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

        private val RECONNECT_DELAY_INITIAL = 1.seconds
        private val RECONNECT_DELAY_MAX = 30.seconds

        private val noopCipher = object : SimpleCipher {
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
     * @throws HttpTransport.ConnectionException if unable to connect.
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

    /**
     * Connects to the remote wallet server, waiting for the server connection if needed.
     */
    private suspend fun waitForWalletServer(): WalletServer {
        var delay = RECONNECT_DELAY_INITIAL
        while (true) {
            try {
                return getWalletServer()
            } catch (err: HttpTransport.ConnectionException) {
                delay(delay)
                delay *= 2
                if (delay > RECONNECT_DELAY_MAX) {
                    delay = RECONNECT_DELAY_MAX
                }
            }
        }
    }

    /**
     * Gets issuing authority by its id, caching instances. If unable to connect, suspend
     * and wait until connecting is possible.
     */
    suspend fun getIssuingAuthority(issuingAuthorityId: String): IssuingAuthority {
        val instance = waitForWalletServer()
        instanceLock.withLock {
            var issuingAuthority = issuingAuthorityMap[issuingAuthorityId]
            if (issuingAuthority == null) {
                issuingAuthority = instance.getIssuingAuthority(issuingAuthorityId)
                issuingAuthorityMap[issuingAuthorityId] = issuingAuthority
            }
            return issuingAuthority
        }
    }

    private suspend fun getWalletServerUnlocked(): WalletServer {
        val dispatcher: FlowDispatcher
        val notifier: FlowNotifier
        val exceptionMapBuilder = FlowExceptionMap.Builder()
        WalletServerState.registerExceptions(exceptionMapBuilder)
        if (baseUrl == "dev:") {
            val builder = FlowDispatcherLocal.Builder()
            WalletServerState.registerAll(builder)
            notifier = FlowNotificationsLocal(noopCipher)
            val environment = LocalDevelopmentEnvironment(
                context, settingsModel, secureArea, notifier)
            dispatcher = WrapperFlowDispatcher(builder.build(
                environment,
                noopCipher,
                exceptionMapBuilder.build()
            ))
        } else {
            val httpClient = WalletHttpTransport(baseUrl)
            val poll = FlowPollHttp(httpClient)
            notifier = FlowNotifierPoll(poll)
            CoroutineScope(Dispatchers.IO).launch {
                notifier.loop()
            }
            dispatcher = FlowDispatcherHttp(httpClient, exceptionMapBuilder.build())
        }

        // "root" is the entry point for the server, see FlowState annotation on
        // com.android.identity.issuance.wallet.WalletServerState
        val walletServer = WalletServerImpl(
            flowPath = "root",
            flowState = Bstr(byteArrayOf()),
            flowDispatcher = dispatcher,
            flowNotifier = notifier
        )

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
            val attestation = keyInfo.attestation
            val seq = extractAttestationSequence(attestation.certChain!!)
            val clientId = String(ASN1OctetString.getInstance(seq.getObjectAt(4)).octets)
            challenge = authentication.requestChallenge(clientId)
            if (clientId != challenge.clientId) {
                secureArea.deleteKey(alias)
                keyInfo = null
            }
        }
        val newClient = keyInfo == null
        if (newClient) {
            secureArea.createKey(alias,
                AndroidKeystoreCreateKeySettings.Builder(
                    challenge!!.clientId.toByteArray()
                ).build()
            )
            keyInfo = secureArea.getKeyInfo(alias)
        }
        val message = authenticationMessage(challenge!!.clientId, challenge.nonce)
        _walletServerCapabilities = authentication.authenticate(ClientAuthentication(
            secureArea.sign(alias, Algorithm.ES256, message.toByteArray(), null),
            if (newClient) {
                keyInfo!!.attestation.certChain!!
            } else null,
            getWalletApplicationCapabilities()
        ))
        authentication.complete()
        storageEngine.put("WalletServerCapabilities", _walletServerCapabilities!!.toCbor())

        return walletServer
    }

    /**
     * Flow handler that delegates to a base, ensuring that the work is done in IO dispatchers
     * and logging the calls.
     */
    internal class WrapperFlowDispatcher(private val base: FlowDispatcher): FlowDispatcher {
        companion object {
            const val TAG = "FlowRpc"
        }

        override val exceptionMap: FlowExceptionMap
            get() = base.exceptionMap

        override suspend fun dispatch(flow: String, method: String, args: List<DataItem>): List<DataItem> {
            Logger.i(TAG, "POST [${Thread.currentThread().name}] $flow/$method")
            val start = System.nanoTime()
            try {
                return withContext(Dispatchers.IO) {
                    base.dispatch(flow, method, args)
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