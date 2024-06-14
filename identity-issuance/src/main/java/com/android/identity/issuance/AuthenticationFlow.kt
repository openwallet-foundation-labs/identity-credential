package com.android.identity.issuance

import com.android.identity.flow.client.FlowBase
import com.android.identity.flow.annotation.FlowInterface
import com.android.identity.flow.annotation.FlowMethod

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