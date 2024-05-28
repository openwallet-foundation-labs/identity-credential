package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowJoin
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.environment.Configuration
import com.android.identity.flow.environment.Resources
import com.android.identity.flow.environment.FlowEnvironment
import com.android.identity.flow.handler.FlowHandlerLocal
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.WalletServer
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

    private fun getIssuingAuthorityConfiguration(
        env: FlowEnvironment,
        id: String,
    ): IssuingAuthorityConfiguration {
        val configuration = env.getInterface(Configuration::class)!!
        val resources = env.getInterface(Resources::class)!!
        val prefix = "issuing_authorities.$id"
        val logoPath = configuration.getProperty("$prefix.logo") ?: "default/logo.png"
        val logo = resources.getRawResource(logoPath)!!
        val artPath =
            configuration.getProperty("$prefix.card_art") ?: "default/card_art.png"
        val art = resources.getRawResource(artPath)!!
        val requireUserAuthenticationToViewDocument =
            configuration.getBool("$prefix.require_user_authentication_to_view_document", false)
        return IssuingAuthorityConfiguration(
            identifier = id,
            issuingAuthorityName = configuration.getProperty("$prefix.name") ?: "Untitled",
            issuingAuthorityLogo = logo.toByteArray(),
            issuingAuthorityDescription = configuration.getProperty("$prefix.description") ?: "Unknown",
            pendingDocumentInformation = DocumentConfiguration(
                displayName = "Pending",
                typeDisplayName = "Driving License",
                cardArt = art.toByteArray(),
                requireUserAuthenticationToViewDocument = requireUserAuthenticationToViewDocument,
                mdocConfiguration = null,
                sdJwtVcDocumentConfiguration = null
            )
        )
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
            val resources = env.getInterface(Resources::class)!!
            return list.map { idElem ->
                val id = idElem.jsonPrimitive.content
                val prefix = "issuing_authorities.$id"
                val logoPath = configuration.getProperty("$prefix.logo") ?: "default/logo.png"
                val logo = resources.getRawResource(logoPath)!!
                val artPath =
                    configuration.getProperty("$prefix.card_art") ?: "default/card_art.png"
                val art = resources.getRawResource(artPath)!!
                val requireUserAuthenticationToViewDocument =
                    configuration.getProperty("$prefix.require_user_authentication_to_view_document") != "false"
                IssuingAuthorityConfiguration(
                    identifier = id,
                    issuingAuthorityName = configuration.getProperty("$prefix.name") ?: "Untitled",
                    issuingAuthorityLogo = logo.toByteArray(),
                    issuingAuthorityDescription = configuration.getProperty("$prefix.description") ?: "Unknown",
                    pendingDocumentInformation = DocumentConfiguration(
                        displayName = "Pending",
                        typeDisplayName = "Driving License",
                        cardArt = art.toByteArray(),
                        requireUserAuthenticationToViewDocument = requireUserAuthenticationToViewDocument,
                        mdocConfiguration = null,
                        sdJwtVcDocumentConfiguration = null
                    )
                )
            }
        }
    }

    @FlowMethod
    suspend fun getIssuingAuthority(
        env: FlowEnvironment,
        identifier: String
    ): IssuingAuthorityState {
        check(clientId.isNotEmpty())
        return IssuingAuthorityState(
            clientId,
            identifier,
            getIssuingAuthorityConfiguration(env, identifier)
        )
    }
}
