package com.android.identity.securearea.software

import com.android.identity.securearea.EcCurve
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.KeyPurpose
import java.security.cert.X509Certificate

/**
 * Specialization of [KeyInfo] specific to software-backed keys.
 *
 * @param isPassphraseProtected whether the key is passphrase protected.
 */
class SoftwareKeyInfo internal constructor(
    attestation: List<X509Certificate>,
    keyPurposes: Set<KeyPurpose>,
    ecCurve: EcCurve,
    isHardwareBacked: Boolean,
    val isPassphraseProtected: Boolean
): KeyInfo(
    attestation,
    keyPurposes,
    ecCurve,
    isHardwareBacked
)