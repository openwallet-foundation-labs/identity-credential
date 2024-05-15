package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.handler.FlowEnvironment
import com.android.identity.issuance.RegistrationConfiguration
import com.android.identity.issuance.RegistrationFlow
import com.android.identity.issuance.RegistrationResponse

/**
 * State of [RegistrationFlow] RPC implementation.
 */
@FlowState(flowInterface = RegistrationFlow::class)
@CborSerializable
class RegistrationState(
    val documentId: String = "",
    var response: RegistrationResponse? = null
) {
    companion object

    @FlowMethod
    fun getDocumentRegistrationConfiguration(env: FlowEnvironment): RegistrationConfiguration {
        return RegistrationConfiguration(documentId)
    }

    @FlowMethod
    fun sendDocumentRegistrationResponse(env: FlowEnvironment, response: RegistrationResponse) {
        this.response = response
    }
}