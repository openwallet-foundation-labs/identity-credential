package org.multipaz.provisioning.openid4vci

import org.multipaz.flow.annotation.FlowState
import org.multipaz.provisioning.CredentialConfiguration
import org.multipaz.provisioning.CredentialFormat
import org.multipaz.provisioning.RequestCredentialsFlow

@FlowState(
    flowInterface = RequestCredentialsFlow::class
)
abstract class AbstractRequestCredentials(
    val documentId: String,
    val credentialConfiguration: CredentialConfiguration,
    var format: CredentialFormat? = null,
) {
    companion object
}
