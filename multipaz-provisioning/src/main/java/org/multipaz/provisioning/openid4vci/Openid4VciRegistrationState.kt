package org.multipaz.provisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcMethod
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.provisioning.RegistrationConfiguration
import org.multipaz.provisioning.Registration
import org.multipaz.provisioning.RegistrationResponse
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthInspector

@RpcState(endpoint = "openid4vci.registration")
@CborSerializable
class Openid4VciRegistrationState(
    val documentId: String,
    var response: RegistrationResponse? = null
): Registration, RpcAuthInspector by RpcAuthBackendDelegate {
    override suspend fun getDocumentRegistrationConfiguration(): RegistrationConfiguration {
        return RegistrationConfiguration(documentId)
    }

    override suspend fun sendDocumentRegistrationResponse(response: RegistrationResponse) {
        this.response = response
    }

    companion object
}