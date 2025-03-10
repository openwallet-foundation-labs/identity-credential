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
 * @param algorithm a fully specified [Algorithm] for the key.
 * @param publicKey the public part of the key.
 * @param attestation the attestation for the key.
 */
open class KeyInfo protected constructor(
    val alias: String,
    val algorithm: Algorithm,
    val publicKey: EcPublicKey,
    val attestation: KeyAttestation
)