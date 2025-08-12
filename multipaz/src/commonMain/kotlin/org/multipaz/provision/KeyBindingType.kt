package org.multipaz.provision

import org.multipaz.crypto.Algorithm
import org.multipaz.securearea.KeyAttestation

/**
 * Type of [KeyBindingInfo] that credential issuer expects in [ProvisioningClient.obtainCredentials]
 * call.
 */
sealed class KeyBindingType {
    /**
     * No key binding, [KeyBindingInfo.Keyless] should be used.
     */
    object Keyless: KeyBindingType()

    /**
     * Binding key(s) should be sent along with Openid4Vci-formatted proof-of-possession using
     * [KeyBindingInfo.OpenidProofOfPossession].
     */
    data class OpenidProofOfPossession(
        val algorithm: Algorithm
    ): KeyBindingType()

    /**
     * Binding key should be sent as part of key attestation (as defined by [KeyAttestation]
     * object) using [KeyBindingInfo.Attestation].
     */
    data class Attestation(
        val algorithm: Algorithm
    ): KeyBindingType()
}