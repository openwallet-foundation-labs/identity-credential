package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.RegistrationConfiguration
import com.android.identity.issuance.RegistrationFlow
import com.android.identity.issuance.RegistrationResponse

@FlowState(
    flowInterface = RegistrationFlow::class
)
@CborSerializable
class FunkeRegistrationState(
    val documentId: String,
    var response: RegistrationResponse? = null
) {
    companion object

    @FlowMethod
    suspend fun getDocumentRegistrationConfiguration(env: FlowEnvironment): RegistrationConfiguration {
        return RegistrationConfiguration(documentId)
    }

    @FlowMethod
    suspend fun sendDocumentRegistrationResponse(env: FlowEnvironment, response: RegistrationResponse) {
        this.response = response
    }
}