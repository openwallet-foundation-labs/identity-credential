package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.CredentialRequest
import com.android.identity.issuance.RequestCredentialsFlow

@FlowState(
    flowInterface = RequestCredentialsFlow::class
)
@CborSerializable
class FunkeRequestCredentialsState {
    companion object

    @FlowMethod
    fun getCredentialConfiguration(env: FlowEnvironment, format: CredentialFormat): CredentialConfiguration {
        throw RuntimeException()
    }

    @FlowMethod
    fun sendCredentials(env: FlowEnvironment, credentialRequests: List<CredentialRequest>) {
        throw RuntimeException()
    }
}