package com.android.identity.securearea

import com.android.identity.crypto.EcPublicKey

/**
 * Class with information about a key.
 *
 * Concrete [SecureArea] implementations may subclass this to provide additional
 * implementation-specific information about the key.
 *
 * @param publicKey the public part of the key.
 * @param keyPurposes the purposes of the key.
 * @param attestation the attestation for the key.
 */
open class KeyInfo protected constructor(
    val publicKey: EcPublicKey,
    val keyPurposes: Set<KeyPurpose>,
    val attestation: KeyAttestation
)