package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.CertificateChain
import kotlinx.io.bytestring.ByteString

/**
 * An data structure sent from the Wallet Application to the Wallet Server used to prove
 * that it is the legitimate instance for clientId.
 */
@CborSerializable
data class ClientAuthentication(
    /**
     * An ECDSA signature made by WalletAppliactionKey.
     *
     * TODO: describe what we're actually signing here.
     */
    val signature: ByteString,

    /**
     * The attestation for WalletAppliactionKey.
     *
     * This is only set if this is the first time the client is authenticating.
     */
    val certificateChain: CertificateChain?,

    /**
     * The capabilities of the Wallet Application.
     *
     * This is sent every time the wallet app authenticates to the wallet server.
     */
    val walletApplicationCapabilities: WalletApplicationCapabilities
)
