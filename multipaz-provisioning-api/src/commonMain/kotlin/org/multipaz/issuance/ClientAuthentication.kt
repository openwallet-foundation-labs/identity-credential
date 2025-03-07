package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.device.DeviceAttestation
import org.multipaz.device.AssertionNonce
import org.multipaz.device.DeviceAssertion

/**
 * An data structure sent from the Wallet Application to the Wallet Server used to prove
 * that it is the legitimate instance for clientId.
 */
@CborSerializable
data class ClientAuthentication(
    /**
     * Device attestation (using clientId bytes as nonce).
     *
     * This is only set if this is the first time the client is authenticating.
     */
    val attestation: DeviceAttestation?,

    /**
     * Assertion that proves device integrity by creating assertion for [AssertionNonce].
     *
     * Uses nonce from [ClientChallenge.nonce] supplied by the server.
     */
    val assertion: DeviceAssertion,

    /**
     * The capabilities of the Wallet Application.
     *
     * This is sent every time the wallet app authenticates to the wallet server.
     */
    val walletApplicationCapabilities: WalletApplicationCapabilities
)
