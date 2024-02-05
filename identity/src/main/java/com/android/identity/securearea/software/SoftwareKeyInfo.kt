package com.android.identity.securearea.software

import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.EcPublicKey
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.KeyPurpose

/**
 * Specialization of [KeyInfo] specific to software-backed keys.
 *
 * @param isPassphraseProtected whether the key is passphrase protected.
 */
class SoftwareKeyInfo internal constructor(
    publicKey: EcPublicKey,
    attestation: CertificateChain,
    keyPurposes: Set<KeyPurpose>,
    val isPassphraseProtected: Boolean
): KeyInfo(
    publicKey,
    attestation,
    keyPurposes
)