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
import com.android.identity.issuance.ApplicationSupport
import com.android.identity.issuance.ClientAuthentication
import com.android.identity.issuance.ClientChallenge
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.WalletApplicationCapabilities
import com.android.identity.issuance.WalletServer
import com.android.identity.issuance.WalletServerImpl
import com.android.identity.issuance.authenticationMessage
import com.android.identity.issuance.extractAttestationSequence
import com.android.identity.issuance.wallet.WalletServerState
import com.android.identity.securearea.KeyInfo
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
 *
 * Wallet app can function in two modes: full wallet server mode and minimalistic server mode.
 *
 * In full wallet server mode, wallet app communicates to the wallet server and the wallet server
 * communicates to the actual issuing authority server(s). The advantage of this mode is that
 * the system tends to be much more robust when the app needs to communicate to its own server
 * only. In particular this allows wallet app vendor to decouple evolution of the wallet app from
 * the evolution of the multitude of the issuing authority servers. Also, wallet server
 * functionality only needs to be implemented once and not for every client/mobile platform.
 *
 * In minimalistic server mode, the bulk of the "wallet server" functionality runs on the client
 * and thus the wallet app communicates to the issuing authority servers directly. Only the
 * functionality that cannot be done on the client is delegated on the server. The advantage of
 * this mode is that it tends to be easier to set up and use for development. Also, this mode
 * has less potential privacy issues, in particular if proofing data and credentials are not
 * end-to-end encrypted.
 */
class WalletServerProvider(
    private val context: Context,
    private val secureArea: AndroidKeystoreSecureArea,
    private val settingsModel: SettingsModel,
    private val getWalletApplicationCapabilities: suspend () -> WalletApplicationCapabilities
) {
    private val instanceLock = Mutex()
    private var instance: WalletServer? = null
    private val issuingAuthorityMap = mutableMapOf<String, IssuingAuthority>()
    private var applicationSupportSupplier: ApplicationSupportSupplier? = null

    private var notificationsJob: Job? = null
    private var resetListeners = mutableListOf<()->Unit>()

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
        settingsModel.walletServerUrl.observeForever {
            clearInstance()
        }
        settingsModel.minServerUrl.observeForever {
            if (settingsModel.walletServerUrl.value == "dev:") {
                clearInstance()
            }
        }
    }

    private fun clearInstance() {
        if (instance == null) {
            // Nothing to do, and we don't want to notify reset listeners
            return
        }
        runBlocking {
            instanceLock.withLock {
                Logger.i(TAG, "Resetting wallet server...")
                try {
                    for (issuingAuthority in issuingAuthorityMap.values) {
                        issuingAuthority.complete()
                    }
                    applicationSupportSupplier?.release()
                    instance?.complete()
                } catch (err: Exception) {
                    Logger.e(TAG, "Error shutting down Wallet Server connection", err)
                }
                issuingAuthorityMap.clear()
                applicationSupportSupplier = null
                instance = null
                notificationsJob?.cancel()
                notificationsJob = null
                Logger.i(TAG, "Done resetting wallet server")
            }
            resetListeners.forEach { it() }
        }
    }

    fun addResetListener(listener: () -> Unit) {
        resetListeners.add(listener)
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
                Logger.i(TAG, "Creating new WalletServer instance: $baseUrl")
                val server = getWalletServerUnlocked(baseUrl)
                instance = server.first
                applicationSupportSupplier = server.second
                Logger.i(TAG, "Created new WalletServer instance: $baseUrl")
            } else {
                Logger.i(TAG, "Reusing existing WalletServer instance: $baseUrl")
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

    /**
     * Gets ApplicationSupport object. It always comes from the server (either full wallet server
     * or minimal wallet server).
     */
    suspend fun getApplicationSupport(): ApplicationSupport {
        getWalletServer()
        return applicationSupportSupplier!!.getApplicationSupport()
    }

    private suspend fun getWalletServerUnlocked(
        baseUrl: String
    ): Pair<WalletServer, ApplicationSupportSupplier> {
        val dispatcher: FlowDispatcher
        val notifier: FlowNotifier
        val exceptionMapBuilder = FlowExceptionMap.Builder()
        var applicationSupportSupplier: ApplicationSupportSupplier? = null
        WalletServerState.registerExceptions(exceptionMapBuilder)
        if (baseUrl == "dev:") {
            val builder = FlowDispatcherLocal.Builder()
            WalletServerState.registerAll(builder)
            notifier = FlowNotificationsLocal(noopCipher)
            applicationSupportSupplier = ApplicationSupportSupplier() {
                val minServer = getWalletServerUnlocked(settingsModel.minServerUrl.value!!)
                minServer.second.getApplicationSupport()
            }
            val environment = LocalDevelopmentEnvironment(
                context, settingsModel, secureArea, notifier, applicationSupportSupplier)
            dispatcher = WrapperFlowDispatcher(builder.build(
                environment,
                noopCipher,
                exceptionMapBuilder.build()
            ))
        } else {
            val httpClient = WalletHttpTransport(baseUrl)
            val poll = FlowPollHttp(httpClient)
            notifier = FlowNotifierPoll(poll)
            notificationsJob = CoroutineScope(Dispatchers.IO).launch {
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
        authentication.authenticate(ClientAuthentication(
            secureArea.sign(alias, Algorithm.ES256, message.toByteArray(), null),
            if (newClient) {
                keyInfo!!.attestation.certChain!!
            } else null,
            getWalletApplicationCapabilities()
        ))
        authentication.complete()

        if (applicationSupportSupplier == null) {
            applicationSupportSupplier = ApplicationSupportSupplier {
                walletServer.applicationSupport()
            }
        }

        return Pair(walletServer, applicationSupportSupplier)
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

    internal class ApplicationSupportSupplier(val factory: suspend () -> ApplicationSupport) {
        private var applicationSupport: ApplicationSupport? = null

        suspend fun getApplicationSupport(): ApplicationSupport {
            if (applicationSupport == null) {
                applicationSupport = factory()
            }
            return applicationSupport!!
        }

        suspend fun release() {
            applicationSupport?.complete()
        }
    }
}