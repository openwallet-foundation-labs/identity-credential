package org.multipaz.legacyprovisioning.wallet

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.handler.RpcDispatcherLocal
import org.multipaz.rpc.handler.RpcExceptionMap
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import org.multipaz.legacyprovisioning.ApplicationSupport
import org.multipaz.legacyprovisioning.DocumentConfiguration
import org.multipaz.legacyprovisioning.IssuingAuthorityConfiguration
import org.multipaz.legacyprovisioning.IssuingAuthorityException
import org.multipaz.legacyprovisioning.LandingUrlUnknownException
import org.multipaz.legacyprovisioning.ProvisioningBackend
import org.multipaz.legacyprovisioning.ProvisioningBackendSettings
import org.multipaz.legacyprovisioning.openid4vci.Openid4VciIssuingAuthorityState
import org.multipaz.legacyprovisioning.openid4vci.Openid4VciProofingState
import org.multipaz.legacyprovisioning.openid4vci.Openid4VciRegistrationState
import org.multipaz.legacyprovisioning.openid4vci.RequestCredentialsUsingKeyAttestation
import org.multipaz.legacyprovisioning.openid4vci.RequestCredentialsUsingProofOfPossession
import org.multipaz.legacyprovisioning.openid4vci.register
import org.multipaz.legacyprovisioning.hardcoded.IssuingAuthorityState
import org.multipaz.legacyprovisioning.hardcoded.ProofingState
import org.multipaz.legacyprovisioning.hardcoded.RegistrationState
import org.multipaz.legacyprovisioning.hardcoded.RequestCredentialsState
import org.multipaz.legacyprovisioning.hardcoded.register
import org.multipaz.legacyprovisioning.register
import org.multipaz.legacyprovisioning.IssuingAuthority
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspector
import kotlin.coroutines.coroutineContext

@RpcState(
    endpoint = "root",
    creatable = true
)
@CborSerializable
class ProvisioningBackendState: ProvisioningBackend, RpcAuthInspector by RpcAuthBackendDelegate {
    companion object {
        private const val TAG = "WalletServerState"

        private fun devConfig(env: BackendEnvironment): IssuingAuthorityConfiguration {
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

        fun registerExceptions(exceptionMapBuilder: RpcExceptionMap.Builder) {
            IssuingAuthorityException.register(exceptionMapBuilder)
            LandingUrlUnknownException.register(exceptionMapBuilder)
        }

        fun registerAll(dispatcher: RpcDispatcherLocal.Builder) {
            ProvisioningBackendState.register(dispatcher)
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

    override suspend fun applicationSupport(): ApplicationSupportState {
        if (BackendEnvironment.getInterface(ApplicationSupport::class) != null) {
            // If this interface resolves, it means we are running in-app. But ApplicationSupport
            // is not going to work properly when run in-app.
            throw IllegalStateException("Only server-side ApplicationSupport must be used")
        }
        return ApplicationSupportState(RpcAuthContext.getClientId())
    }

    override suspend fun getIssuingAuthorityConfigurations(): List<IssuingAuthorityConfiguration> {
        val settings = ProvisioningBackendSettings(BackendEnvironment.getInterface(Configuration::class)!!)
        val issuingAuthorityList = settings.getStringList("issuingAuthorityList")
        return if (issuingAuthorityList.isEmpty()) {
            listOf(devConfig(BackendEnvironment.get(coroutineContext)))
        } else {
            issuingAuthorityList.map { idElem ->
                IssuingAuthorityState.getConfiguration(idElem)
            }
        }
    }

    override suspend fun getIssuingAuthority(
        identifier: String
    ): IssuingAuthority {
        val clientId = RpcAuthContext.getClientId()
        if (identifier.startsWith("openid4vci#")) {
            val parts = identifier.split("#")
            if (parts.size != 3) {
                throw IllegalStateException("Invalid openid4vci id")
            }
            val credentialIssuerUri = parts[1]
            val credentialConfigurationId = parts[2]
            // NB: applicationSupport will only be non-null when running this code locally in the
            // Android Wallet app.
            val applicationSupport = BackendEnvironment.getInterface(ApplicationSupport::class)
            val issuanceClientId = applicationSupport?.getClientAssertionId(credentialIssuerUri)
                ?: ApplicationSupportState(clientId).getClientAssertionId(credentialIssuerUri)
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
