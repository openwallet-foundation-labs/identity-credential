package org.multipaz.issuance.funke

import org.multipaz.flow.annotation.FlowState
import org.multipaz.issuance.CredentialConfiguration
import org.multipaz.issuance.CredentialFormat
import org.multipaz.issuance.RequestCredentialsFlow

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
