package com.android.identity.issuance

import com.android.identity.flow.FlowBaseInterface
import com.android.identity.flow.annotation.FlowGetter
import com.android.identity.flow.annotation.FlowInterface
import com.android.identity.flow.annotation.FlowMethod
import kotlinx.io.bytestring.ByteString
import org.intellij.lang.annotations.Identifier

@FlowInterface
interface WalletServer: FlowBaseInterface {
    /**
     * No need to call on client-side if using a [WalletServer] obtained from a
     * [WalletServerProvider].
     */
    @FlowMethod
    suspend fun authenticate(): AuthenticationFlow

    /**
     * Static information about the available Issuing Authorities.
     *
     * Queried from all issuing authorities at initialization time.
     */
    @FlowMethod
    suspend fun getIssuingAuthorityConfigurations(): List<IssuingAuthorityConfiguration>

    @FlowMethod
    suspend fun getIssuingAuthority(identifier: String): IssuingAuthority
}