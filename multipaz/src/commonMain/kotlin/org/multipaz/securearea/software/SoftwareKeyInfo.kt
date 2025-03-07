package org.multipaz.securearea.software

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPublicKey
import org.multipaz.securearea.KeyAttestation
import org.multipaz.securearea.KeyInfo
import org.multipaz.securearea.KeyPurpose
import org.multipaz.securearea.PassphraseConstraints

/**
 * Specialization of [KeyInfo] specific to software-backed keys.
 *
 * @param isPassphraseProtected whether the key is passphrase protected.
 * @param passphraseConstraints constraints on the passphrase, if any.
 */
class SoftwareKeyInfo internal constructor(
    alias: String,
    publicKey: EcPublicKey,
    attestation: KeyAttestation,
    keyPurposes: Set<KeyPurpose>,
    signingAlgorithm: Algorithm,
    val isPassphraseProtected: Boolean,
    val passphraseConstraints: PassphraseConstraints?
): KeyInfo(
    alias,
    publicKey,
    keyPurposes,
    signingAlgorithm,
    attestation
)