package org.multipaz.testapp.provisioning

import org.multipaz.device.DeviceCheck
import org.multipaz.device.AssertionNonce
import org.multipaz.device.DeviceAttestation
import org.multipaz.rpc.handler.RpcDispatcher
import org.multipaz.rpc.handler.RpcDispatcherHttp
import org.multipaz.rpc.handler.RpcExceptionMap
import org.multipaz.rpc.handler.RpcNotifier
import org.multipaz.rpc.handler.RpcNotifierPoll
import org.multipaz.rpc.handler.RpcPollHttp
import org.multipaz.rpc.transport.HttpTransport
import org.multipaz.provisioning.ClientAuthentication
import org.multipaz.provisioning.IssuingAuthority
import org.multipaz.provisioning.IssuingAuthorityException
import org.multipaz.provisioning.LandingUrlUnknownException
import org.multipaz.provisioning.WalletApplicationCapabilities
import org.multipaz.provisioning.WalletServer
import org.multipaz.provisioning.register
import org.multipaz.storage.StorageTableSpec
import org.multipaz.testapp.platformSecureAreaProvider
import org.multipaz.testapp.platformStorage
import org.multipaz.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.provisioning.AuthenticationStub
import org.multipaz.provisioning.WalletServerStub
import org.multipaz.rpc.handler.RpcAuthIssuerAssertion
import org.multipaz.rpc.handler.RpcDispatcherAuth
import kotlin.time.Duration.Companion.seconds

/**
 * An object used to connect to a remote wallet server.
 */
class WalletServerProvider(
    private val baseUrl: String,
    private val getWalletApplicationCapabilities: suspend () -> WalletApplicationCapabilities,
) {
    private val instanceLock = Mutex()
    private var instance: WalletServer? = null
    private val issuingAuthorityMap = mutableMapOf<String, IssuingAuthority>()

    private var notificationsJob: Job? = null

    companion object {
        private const val TAG = "WalletServerProvider"

        private val RECONNECT_DELAY_INITIAL = 1.seconds
        private val RECONNECT_DELAY_MAX = 30.seconds

        private val serverTableSpec = StorageTableSpec(
            name = "Servers",
            supportPartitions = false,
            supportExpiration = false
        )
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
                instance = getWalletServerUnlocked(baseUrl)
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
        var delay = RECONNECT_DELAY_INITIAL
        while (true) {
            try {
                instanceLock.withLock {
                    var issuingAuthority = issuingAuthorityMap[issuingAuthorityId]
                    if (issuingAuthority == null) {
                        issuingAuthority = instance.getIssuingAuthority(issuingAuthorityId)
                        issuingAuthorityMap[issuingAuthorityId] = issuingAuthority
                    }
                    return issuingAuthority
                }
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
     * Creates an Issuing Authority by the [credentialIssuerUri] and [credentialConfigurationId],
     * caching instances. If unable to connect, suspend and wait until connecting is possible.
     */
    suspend fun createOpenid4VciIssuingAuthorityByUri(
        credentialIssuerUri:String,
        credentialConfigurationId: String
    ): IssuingAuthority {
        // Not allowed per spec, but double-check, so there are no surprises.
        check(credentialIssuerUri.indexOf('#') < 0)
        check(credentialConfigurationId.indexOf('#') < 0)
        val id = "openid4vci#$credentialIssuerUri#$credentialConfigurationId"
        return getIssuingAuthority(id)
    }

    private suspend fun getWalletServerUnlocked(baseUrl: String): WalletServer {
        val dispatcher: RpcDispatcher
        val notifier: RpcNotifier
        val exceptionMapBuilder = RpcExceptionMap.Builder()
        IssuingAuthorityException.register(exceptionMapBuilder)
        LandingUrlUnknownException.register(exceptionMapBuilder)
        val httpClient = WalletHttpTransport(baseUrl)
        val poll = RpcPollHttp(httpClient)
        notifier = RpcNotifierPoll(poll)
        notificationsJob = CoroutineScope(Dispatchers.Main).launch {
            notifier.loop()
        }
        dispatcher = RpcDispatcherHttp(httpClient, exceptionMapBuilder.build())

        val serverTable = platformStorage().getTable(serverTableSpec)
        var serverData = serverTable.get(key = baseUrl)?.let {
            ServerData.fromCbor(it.toByteArray())
        }

        val secureAreaProvider = platformSecureAreaProvider()

        // RPC entry point that does not require authorization, it is used to set up
        // authorization parameters with the server (so these parameters can be used for subsequent
        // RPC communication).
        val authentication = AuthenticationStub(
            endpoint = "auth",
            dispatcher = dispatcher,
            notifier = notifier
        )

        val challenge = authentication.requestChallenge(serverData?.clientId ?: "")
        val deviceAttestation: DeviceAttestation?
        if (serverData?.clientId != challenge.clientId) {
            // new client
            val result = DeviceCheck.generateAttestation(
                secureAreaProvider.get(),
                challenge.clientId.encodeToByteString()
            )
            deviceAttestation = result.deviceAttestation
            val newServerData = ServerData(
                clientId = challenge.clientId,
                deviceAttestationId = result.deviceAttestationId
            )
            if (serverData == null) {
                serverTable.insert(
                    data = ByteString(newServerData.toCbor()),
                    key = baseUrl
                )
            } else {
                serverTable.update(
                    data = ByteString(newServerData.toCbor()),
                    key = baseUrl
                )
            }
            serverData = newServerData
        } else {
            deviceAttestation = null
        }

        authentication.authenticate(ClientAuthentication(
            deviceAttestation,
            DeviceCheck.generateAssertion(
                secureAreaProvider.get(),
                serverData.deviceAttestationId,
                AssertionNonce(challenge.nonce)
            ),
            getWalletApplicationCapabilities()
        ))

        val authorizedDispatcher = RpcDispatcherAuth(
            base = dispatcher,
            rpcAuthIssuer = RpcAuthIssuerAssertion(
                clientId = serverData.clientId,
                secureArea = secureAreaProvider.get(),
                deviceAttestationId = serverData.deviceAttestationId
            )
        )

        // "root" is the entry point for the server, see FlowState annotation on
        // org.multipaz.issuance.wallet.WalletServerState
        return WalletServerStub(
            endpoint = "root",
            dispatcher = dispatcher,
            notifier = notifier
        )
    }
}