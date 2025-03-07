package org.multipaz.issuance.funke

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.issuance.RegistrationConfiguration
import org.multipaz.issuance.RegistrationFlow
import org.multipaz.issuance.RegistrationResponse

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