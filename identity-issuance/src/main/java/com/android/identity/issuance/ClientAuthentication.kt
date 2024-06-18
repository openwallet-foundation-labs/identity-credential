package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.EcSignature
import com.android.identity.crypto.X509CertChain

/**
 * An data structure sent from the Wallet Application to the Wallet Server used to prove
 * that it is the legitimate instance for clientId.
 */
@CborSerializable
data class ClientAuthentication(
    /**
     * An ECDSA signature made by WalletApplicationKey.
     *
     * TODO: describe what we're actually signing here.
     */
    val signature: EcSignature,

    /**
     * The attestation for WalletApplicationKey.
     *
     * This is only set if this is the first time the client is authenticating.
     */
    val attestation: X509CertChain?,

    /**
     * The capabilities of the Wallet Application.
     *
     * This is sent every time the wallet app authenticates to the wallet server.
     */
    val walletApplicationCapabilities: WalletApplicationCapabilities
)
