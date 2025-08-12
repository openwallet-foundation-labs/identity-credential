package org.multipaz.provision

import org.multipaz.securearea.KeyAttestation

/**
 * Provides keys to which credentials are to be bound and key proofing information.
 */
sealed class KeyBindingInfo {
    /** Credential is not bound to keys. */
    data object Keyless: KeyBindingInfo()

    /** Keys are supplied using Openid4Vci-defined proof-of-possession JWT */
    data class OpenidProofOfPossession(
        val jwtList: List<String>
    )

    /** Keys are supplied using [KeyAttestation] objects */
    data class Attestation(
        val attestations: List<KeyAttestation>
    ): KeyBindingInfo()
}