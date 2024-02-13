package com.android.identity.securearea

import java.security.cert.X509Certificate

/**
 * Class with information about a key.
 *
 * Concrete [SecureArea] implementations may subclass this to provide additional
 * implementation-specific information about the key.
 *
 * @param attestation the attestation for the key.
 * @param keyPurposes the purposes of the key.
 * @param ecCurve the curve for the key.
 * @param isHardwareBacked whether the key is hardware backed.
 */
open class KeyInfo protected constructor(
    val attestation: List<X509Certificate>,
    val keyPurposes: Set<KeyPurpose>,
    val ecCurve: EcCurve,
    val isHardwareBacked: Boolean
)