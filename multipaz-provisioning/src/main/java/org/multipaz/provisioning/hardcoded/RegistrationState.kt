package org.multipaz.provisioning.hardcoded

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.provisioning.RegistrationConfiguration
import org.multipaz.provisioning.Registration
import org.multipaz.provisioning.RegistrationResponse
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthInspector

/**
 * State of [Registration] RPC implementation.
 */
@RpcState(endpoint= "hardcoded.registration")
@CborSerializable
class RegistrationState(
    val documentId: String = "",
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