package org.multipaz.provisioning.wallet

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.flow.annotation.FlowJoin
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.handler.FlowDispatcherLocal
import org.multipaz.flow.handler.FlowExceptionMap
import org.multipaz.flow.server.Configuration
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.flow.server.Resources
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.DocumentConfiguration
import org.multipaz.provisioning.IssuingAuthorityConfiguration
import org.multipaz.provisioning.IssuingAuthorityException
import org.multipaz.provisioning.LandingUrlUnknownException
import org.multipaz.provisioning.WalletServer
import org.multipaz.provisioning.WalletServerSettings
import org.multipaz.provisioning.common.AbstractIssuingAuthorityState
import org.multipaz.provisioning.openid4vci.Openid4VciIssuingAuthorityState
import org.multipaz.provisioning.openid4vci.Openid4VciProofingState
import org.multipaz.provisioning.openid4vci.Openid4VciRegistrationState
import org.multipaz.provisioning.openid4vci.Openid4VciIssuerMetadata
import org.multipaz.provisioning.openid4vci.RequestCredentialsUsingKeyAttestation
import org.multipaz.provisioning.openid4vci.RequestCredentialsUsingProofOfPossession
import org.multipaz.provisioning.openid4vci.register
import org.multipaz.provisioning.hardcoded.IssuingAuthorityState
import org.multipaz.provisioning.hardcoded.ProofingState
import org.multipaz.provisioning.hardcoded.RegistrationState
import org.multipaz.provisioning.hardcoded.RequestCredentialsState
import org.multipaz.provisioning.hardcoded.register
import org.multipaz.provisioning.register
import org.multipaz.util.Logger
import kotlinx.io.bytestring.buildByteString
import kotlin.random.Random

@FlowState(
    flowInterface = WalletServer::class,
    path = "root",
    creatable = true
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
                    directAccessConfiguration = null,
                    sdJwtVcDocumentConfiguration = null
                ),
                numberOfCredentialsToRequest = 3,
                minCredentialValidityMillis = 30 * 24 * 3600L,
                maxUsesPerCredentials = 1
            )
        }

        fun registerExceptions(exceptionMapBuilder: FlowExceptionMap.Builder) {
            IssuingAuthorityException.register(exceptionMapBuilder)
            LandingUrlUnknownException.register(exceptionMapBuilder)
        }

        fun registerAll(dispatcher: FlowDispatcherLocal.Builder) {
            WalletServerState.register(dispatcher)
            ApplicationSupportState.register(dispatcher)
            AuthenticationState.register(dispatcher)
            IssuingAuthorityState.register(dispatcher)
            ProofingState.register(dispatcher)
            RegistrationState.register(dispatcher)
            RequestCredentialsState.register(dispatcher)
            Openid4VciIssuingAuthorityState.register(dispatcher)
            Openid4VciProofingState.register(dispatcher)
            Openid4VciRegistrationState.register(dispatcher)
            RequestCredentialsUsingProofOfPossession.register(dispatcher)
            RequestCredentialsUsingKeyAttestation.register(dispatcher)
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
    fun applicationSupport(env: FlowEnvironment): ApplicationSupportState {
        check(clientId.isNotEmpty())
        if (env.getInterface(ApplicationSupport::class) != null) {
            // If this interface resolves, it means we are running in-app. But ApplicationSupport
            // is not going to work properly when run in-app.
            throw IllegalStateException("Only server-side ApplicationSupport must be used")
        }
        return ApplicationSupportState(clientId)
    }

    @FlowMethod
    suspend fun getIssuingAuthorityConfigurations(env: FlowEnvironment): List<IssuingAuthorityConfiguration> {
        check(clientId.isNotEmpty())
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val issuingAuthorityList = settings.getStringList("issuingAuthorityList")
        return if (issuingAuthorityList.isEmpty()) {
            listOf(devConfig(env))
        } else {
            issuingAuthorityList.map { idElem ->
                IssuingAuthorityState.getConfiguration(env, idElem)
            }
        }
    }

    @FlowMethod
    suspend fun getIssuingAuthority(
        env: FlowEnvironment,
        identifier: String
    ): AbstractIssuingAuthorityState {
        check(clientId.isNotEmpty())
        if (identifier.startsWith("openid4vci#")) {
            val parts = identifier.split("#")
            if (parts.size != 3) {
                throw IllegalStateException("Invalid openid4vci id")
            }
            val credentialIssuerUri = parts[1]
            val credentialConfigurationId = parts[2]
            // NB: applicationSupport will only be non-null when running this code locally in the
            // Android Wallet app.
            val applicationSupport = env.getInterface(ApplicationSupport::class)
            val issuanceClientId = applicationSupport?.getClientAssertionId(credentialIssuerUri)
                ?: ApplicationSupportState(clientId).getClientAssertionId(env, credentialIssuerUri)
            return Openid4VciIssuingAuthorityState(
                clientId = clientId,
                credentialIssuerUri = credentialIssuerUri,
                credentialConfigurationId = credentialConfigurationId,
                issuanceClientId = issuanceClientId
            )
        }
        return IssuingAuthorityState(clientId, identifier)
    }
}
