package com.android.identity.securearea.software

import com.android.identity.crypto.EcPublicKey
import com.android.identity.securearea.KeyAttestation
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.PassphraseConstraints

/**
 * Specialization of [KeyInfo] specific to software-backed keys.
 *
 * @param isPassphraseProtected whether the key is passphrase protected.
 * @param passphraseConstraints constraints on the passphrase, if any.
 */
class SoftwareKeyInfo internal constructor(
    publicKey: EcPublicKey,
    attestation: KeyAttestation,
    keyPurposes: Set<KeyPurpose>,
    val isPassphraseProtected: Boolean,
    val passphraseConstraints: PassphraseConstraints?
): KeyInfo(
    publicKey,
    keyPurposes,
    attestation
)