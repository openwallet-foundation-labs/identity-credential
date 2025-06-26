package org.multipaz.testapp.provisioning.backend

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
import org.multipaz.provisioning.ProvisioningBackend
import org.multipaz.provisioning.register
import org.multipaz.storage.StorageTableSpec
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
import org.multipaz.device.Assertion
import org.multipaz.device.DeviceAssertion
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.AuthenticationStub
import org.multipaz.provisioning.ProvisioningBackendStub
import org.multipaz.rpc.handler.RpcAuthIssuerAssertion
import org.multipaz.rpc.handler.RpcDispatcherAuth
import org.multipaz.util.Platform
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * An object used to connect to a remote wallet server.
 */
class ProvisioningBackendProviderRemote(
    private val baseUrl: String,
    private val getWalletApplicationCapabilities: suspend () -> WalletApplicationCapabilities,
): ProvisioningBackendProvider {
    private val instanceLock = Mutex()
    private var instance: ProvisioningBackend? = null
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
     * @return A [ProvisioningBackend] which can be used to interact with the remote wallet server.
     * @throws HttpTransport.ConnectionException if unable to connect.
     */
    suspend fun getProvisioningBackend(): ProvisioningBackend {
        instanceLock.withLock {
            if (instance == null) {
                Logger.i(TAG, "Creating new WalletServer instance: $baseUrl")
                instance = getProvisioningBackendUnlocked(baseUrl)
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
    private suspend fun waitForProvisioningBackend(): ProvisioningBackend {
        var delay = RECONNECT_DELAY_INITIAL
        while (true) {
            try {
                return getProvisioningBackend()
            } catch (err: HttpTransport.ConnectionException) {
                delay(delay)
                delay *= 2
                if (delay > RECONNECT_DELAY_MAX) {
                    delay = RECONNECT_DELAY_MAX
                }
            }
        }
    }

    override val extraCoroutineContext: CoroutineContext get() = EmptyCoroutineContext

    override suspend fun getApplicationSupport(): ApplicationSupport {
        return getProvisioningBackend().applicationSupport()
    }

    /**
     * Gets issuing authority by its id, caching instances. If unable to connect, suspend
     * and wait until connecting is possible.
     */
    override suspend fun getIssuingAuthority(issuingAuthorityId: String): IssuingAuthority {
        val instance = waitForProvisioningBackend()
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

    override suspend fun makeDeviceAssertion(
        assertionFactory: (clientId: String) -> Assertion
    ): DeviceAssertion {
        val serverTable = Platform.getStorage().getTable(serverTableSpec)
        val serverData = serverTable.get(key = baseUrl)!!.let {
            ServerData.fromCbor(it.toByteArray())
        }
        return DeviceCheck.generateAssertion(
            secureArea = Platform.getSecureArea(),
            deviceAttestationId = serverData.deviceAttestationId,
            assertion = assertionFactory(serverData.clientId)
        )
    }

    private suspend fun getProvisioningBackendUnlocked(baseUrl: String): ProvisioningBackend {
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

        val serverTable = Platform.getStorage().getTable(serverTableSpec)
        var serverData = serverTable.get(key = baseUrl)?.let {
            ServerData.fromCbor(it.toByteArray())
        }

        val secureArea = Platform.getSecureArea()

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
                secureArea,
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
                secureArea,
                serverData.deviceAttestationId,
                AssertionNonce(challenge.nonce)
            ),
            getWalletApplicationCapabilities()
        ))

        val authorizedDispatcher = RpcDispatcherAuth(
            base = dispatcher,
            rpcAuthIssuer = RpcAuthIssuerAssertion(
                clientId = serverData.clientId,
                secureArea = secureArea,
                deviceAttestationId = serverData.deviceAttestationId
            )
        )

        // "root" is the entry point for the server, see FlowState annotation on
        // org.multipaz.issuance.wallet.WalletServerState
        return ProvisioningBackendStub(
            endpoint = "root",
            dispatcher = authorizedDispatcher,
            notifier = notifier
        )
    }
}