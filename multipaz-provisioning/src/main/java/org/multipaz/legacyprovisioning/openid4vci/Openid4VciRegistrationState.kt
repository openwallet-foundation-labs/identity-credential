package org.multipaz.legacyprovisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.legacyprovisioning.RegistrationConfiguration
import org.multipaz.legacyprovisioning.Registration
import org.multipaz.legacyprovisioning.RegistrationResponse
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