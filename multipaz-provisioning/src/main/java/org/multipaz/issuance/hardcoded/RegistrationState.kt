package org.multipaz.issuance.hardcoded

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.issuance.RegistrationConfiguration
import org.multipaz.issuance.RegistrationFlow
import org.multipaz.issuance.RegistrationResponse

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