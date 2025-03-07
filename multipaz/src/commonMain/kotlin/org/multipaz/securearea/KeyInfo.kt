package org.multipaz.securearea

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPublicKey

/**
 * Class with information about a key.
 *
 * Concrete [SecureArea] implementations may subclass this to provide additional
 * implementation-specific information about the key.
 *
 * @param alias the alias for the key.
 * @param publicKey the public part of the key.
 * @param keyPurposes the purposes of the key.
 * @param signingAlgorithm algorithm to utilize when this key is used for signing,
 *     only meaningful when [keyPurposes] contains [KeyPurpose.SIGN]
 * @param attestation the attestation for the key.
 */
open class KeyInfo protected constructor(
    val alias: String,
    val publicKey: EcPublicKey,
    val keyPurposes: Set<KeyPurpose>,
    val signingAlgorithm: Algorithm,
    val attestation: KeyAttestation
)