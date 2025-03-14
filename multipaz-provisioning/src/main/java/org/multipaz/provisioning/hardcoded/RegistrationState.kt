package org.multipaz.provisioning.hardcoded

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.provisioning.RegistrationConfiguration
import org.multipaz.provisioning.RegistrationFlow
import org.multipaz.provisioning.RegistrationResponse

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