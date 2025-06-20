package org.multipaz.testapp.provisioning.backend

import org.multipaz.testapp.provisioning.openid4vci.Openid4VciIssuingAuthorityState
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.IssuingAuthority
import org.multipaz.provisioning.IssuingAuthorityConfiguration
import org.multipaz.provisioning.ProvisioningBackend
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.backend.BackendEnvironment

@RpcState
@CborSerializable
class ProvisioningBackendLocal(
    val clientId: String
): ProvisioningBackend {
    override suspend fun applicationSupport(): ApplicationSupport {
        return ApplicationSupportLocal(clientId)
    }

    override suspend fun getIssuingAuthorityConfigurations(): List<IssuingAuthorityConfiguration> {
        return listOf()
    }

    override suspend fun getIssuingAuthority(identifier: String): IssuingAuthority {
        if (identifier.startsWith("openid4vci#")) {
            val parts = identifier.split("#")
            if (parts.size != 3) {
                throw IllegalStateException("Invalid openid4vci id")
            }
            val credentialIssuerUri = parts[1]
            val credentialConfigurationId = parts[2]
            val applicationSupport = BackendEnvironment.getInterface(ApplicationSupport::class)!!
            val issuanceClientId = applicationSupport.getClientAssertionId(credentialIssuerUri)
            return Openid4VciIssuingAuthorityState(
                clientId = clientId,
                credentialIssuerUri = credentialIssuerUri,
                credentialConfigurationId = credentialConfigurationId,
                issuanceClientId = issuanceClientId
            )
        }
        throw IllegalArgumentException("No such issuing authority: '$identifier'")
    }

    companion object
}