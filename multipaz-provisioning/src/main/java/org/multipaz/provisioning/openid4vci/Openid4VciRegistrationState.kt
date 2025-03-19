package org.multipaz.provisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.provisioning.RegistrationConfiguration
import org.multipaz.provisioning.RegistrationFlow
import org.multipaz.provisioning.RegistrationResponse

@FlowState(
    flowInterface = RegistrationFlow::class
)
@CborSerializable
class Openid4VciRegistrationState(
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