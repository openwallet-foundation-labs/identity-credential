package org.multipaz.provisioning

import org.multipaz.rpc.annotation.RpcInterface
import org.multipaz.rpc.annotation.RpcMethod

/**
 * A flow used to authenticate Wallet client (app + device) to the server
 */
@RpcInterface
interface Authentication {
    @RpcMethod
    suspend fun requestChallenge(clientId: String): ClientChallenge

    @RpcMethod
    suspend fun authenticate(auth: ClientAuthentication): ProvisioningBackendCapabilities
}