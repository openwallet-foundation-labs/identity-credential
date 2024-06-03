package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowJoin
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.environment.Configuration
import com.android.identity.flow.environment.Resources
import com.android.identity.flow.environment.FlowEnvironment
import com.android.identity.flow.environment.Notifications
import com.android.identity.flow.handler.FlowHandlerLocal
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.WalletServer
import com.android.identity.util.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.bytestring.buildByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

@FlowState(
    flowInterface = WalletServer::class,
    path = "root"
)
@CborSerializable
class WalletServerState(
    var clientId: String = ""
) {
    companion object {
        private const val TAG = "WalletServerState"

        private fun devConfig(env: FlowEnvironment): IssuingAuthorityConfiguration {
            val resources = env.getInterface(Resources::class)!!
            val logo = resources.getRawResource("default/logo.png")!!
            val art = resources.getRawResource("default/card_art.png")!!
            return IssuingAuthorityConfiguration(
                identifier = "utopia_dev",
                issuingAuthorityName = "Utopia DMV (not configured)",
                issuingAuthorityLogo = logo.toByteArray(),
                issuingAuthorityDescription = "Utopia Driver's License",
                pendingDocumentInformation = DocumentConfiguration(
                    displayName = "Pending",
                    typeDisplayName = "Driving License",
                    cardArt = art.toByteArray(),
                    requireUserAuthenticationToViewDocument = true,
                    mdocConfiguration = null,
                    sdJwtVcDocumentConfiguration = null
                )
            )
        }

        fun registerAll(flowHandlerBuilder: FlowHandlerLocal.Builder) {
            WalletServerState.register(flowHandlerBuilder)
            AuthenticationState.register(flowHandlerBuilder)
            IssuingAuthorityState.register(flowHandlerBuilder)
            ProofingState.register(flowHandlerBuilder)
            RegistrationState.register(flowHandlerBuilder)
            RequestCredentialsState.register(flowHandlerBuilder)
        }

    }

    @FlowMethod
    fun authenticate(env: FlowEnvironment): AuthenticationState {
        return AuthenticationState(nonce = buildByteString { Random.nextBytes(16) })
    }

    @FlowJoin
    fun completeAuthentication(env: FlowEnvironment, authenticationState: AuthenticationState) {
        check(authenticationState.authenticated)
        check(authenticationState.clientId.isNotEmpty())
        this.clientId = authenticationState.clientId
    }

    @FlowMethod
    fun getIssuingAuthorityConfigurations(env: FlowEnvironment): List<IssuingAuthorityConfiguration> {
        check(clientId.isNotEmpty())
        val configuration = env.getInterface(Configuration::class)
        val listStr = configuration?.getProperty("issuing_authority_list")
        if (listStr == null) {
            return listOf(devConfig(env))
        } else {
            val list = Json.parseToJsonElement(listStr).jsonArray
            return list.map { idElem ->
                IssuingAuthorityState.getConfiguration(env, idElem.jsonPrimitive.content)
            }
        }
    }

    @FlowMethod
    fun getIssuingAuthority(env: FlowEnvironment, identifier: String): IssuingAuthorityState {
        check(clientId.isNotEmpty())
        return IssuingAuthorityState(clientId, identifier)
    }

    @FlowMethod
    suspend fun waitForNotification(env: FlowEnvironment): ByteArray {
        val notifications = env.getInterface(Notifications::class)!!

        // The maximum amount of time we want a client to hang around and we throw an
        // error when this is reached. This is to conserve resources on the server
        // side. The wallet app will handle this gracefully by just reconnecting.
        //
        // This should be shorter than the Wallet's client timeout, see REQUEST_TIMEOUT_SECONDS
        // in WalletHttpClient
        //
        val timeoutForClientSeconds = 3*60

        val notificationPayload = withTimeoutOrNull(timeoutForClientSeconds.toLong()*1000) {
            notifications.eventFlow.first { it.first == clientId  }.second
        }
        if (notificationPayload == null) {
            throw NotificationTimeoutError(
                "Timed out waiting for notification (timeout: ${timeoutForClientSeconds} seconds)"
            )
        }
        Logger.i(TAG, "Notification for $clientId: ${Cbor.toDiagnostics(notificationPayload)}")
        return notificationPayload
    }

    class NotificationTimeoutError(message: String): Error(message)
}
