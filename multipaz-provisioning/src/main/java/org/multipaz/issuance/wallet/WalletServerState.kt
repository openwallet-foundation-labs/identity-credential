package org.multipaz.issuance.wallet

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.flow.annotation.FlowJoin
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.handler.FlowDispatcherLocal
import org.multipaz.flow.handler.FlowExceptionMap
import org.multipaz.flow.server.Configuration
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.flow.server.Resources
import org.multipaz.issuance.ApplicationSupport
import org.multipaz.issuance.DocumentConfiguration
import org.multipaz.issuance.IssuingAuthorityConfiguration
import org.multipaz.issuance.IssuingAuthorityException
import org.multipaz.issuance.LandingUrlUnknownException
import org.multipaz.issuance.WalletServer
import org.multipaz.issuance.WalletServerSettings
import org.multipaz.issuance.common.AbstractIssuingAuthorityState
import org.multipaz.issuance.funke.FunkeIssuingAuthorityState
import org.multipaz.issuance.funke.FunkeProofingState
import org.multipaz.issuance.funke.FunkeRegistrationState
import org.multipaz.issuance.funke.Openid4VciIssuerMetadata
import org.multipaz.issuance.funke.RequestCredentialsUsingKeyAttestation
import org.multipaz.issuance.funke.RequestCredentialsUsingProofOfPossession
import org.multipaz.issuance.funke.register
import org.multipaz.issuance.hardcoded.IssuingAuthorityState
import org.multipaz.issuance.hardcoded.ProofingState
import org.multipaz.issuance.hardcoded.RegistrationState
import org.multipaz.issuance.hardcoded.RequestCredentialsState
import org.multipaz.issuance.hardcoded.register
import org.multipaz.issuance.register
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
        // Hard-coded Funke endpoint for PID issuer, could be /c or /c1
        private const val FUNKE_BASE_URL = "https://demo.pid-issuer.bundesdruckerei.de/c"

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
            FunkeIssuingAuthorityState.register(dispatcher)
            FunkeProofingState.register(dispatcher)
            FunkeRegistrationState.register(dispatcher)
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
        val fromConfig = if (issuingAuthorityList.isEmpty()) {
            listOf(devConfig(env))
        } else {
            issuingAuthorityList.map { idElem ->
                IssuingAuthorityState.getConfiguration(env, idElem)
            }
        }
        try {
            // Add everything that Funke server exposes
            val funkeMetadata = Openid4VciIssuerMetadata.get(env, FUNKE_BASE_URL)
            val fromFunkeServer = funkeMetadata.credentialConfigurations.keys.map { id ->
                FunkeIssuingAuthorityState.getConfiguration(env, FUNKE_BASE_URL, id)
            }
            return fromConfig + fromFunkeServer
        } catch (err: Exception) {
            Logger.e(TAG, "Could not reach server $FUNKE_BASE_URL", err)
            return fromConfig
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
            return FunkeIssuingAuthorityState(
                clientId = clientId,
                credentialIssuerUri = credentialIssuerUri,
                credentialConfigurationId = credentialConfigurationId,
                issuanceClientId = issuanceClientId
            )
        }
        return IssuingAuthorityState(clientId, identifier)
    }
}
