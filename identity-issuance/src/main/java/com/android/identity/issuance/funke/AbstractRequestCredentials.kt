package com.android.identity.issuance.funke

import com.android.identity.flow.annotation.FlowState
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.RequestCredentialsFlow

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
