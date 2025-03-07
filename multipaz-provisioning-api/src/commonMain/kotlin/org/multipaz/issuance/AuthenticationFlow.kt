package org.multipaz.issuance

import org.multipaz.flow.client.FlowBase
import org.multipaz.flow.annotation.FlowInterface
import org.multipaz.flow.annotation.FlowMethod

/**
 * A flow used to authenticate Wallet client (app + device) to the server
 */
@FlowInterface
interface AuthenticationFlow : FlowBase {
    @FlowMethod
    suspend fun requestChallenge(clientId: String): ClientChallenge

    @FlowMethod
    suspend fun authenticate(auth: ClientAuthentication): WalletServerCapabilities
}