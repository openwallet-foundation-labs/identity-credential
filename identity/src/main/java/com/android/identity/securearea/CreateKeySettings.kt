package com.android.identity.securearea

/**
 * Base class for key creation settings.
 *
 * This can be used for any conforming [SecureArea] implementations.
 * although such implementations will typically supply their own implementations
 * with additional settings to e.g. configure user authentication, passphrase
 * protections, and other things.
 *
 * @param attestationChallenge the attestation challenge.
 * @param keyPurposes the key purposes.
 * @param ecCurve the curve used.
 */
open class CreateKeySettings(
    val attestationChallenge: ByteArray,
    val keyPurposes: Set<KeyPurpose> = setOf(KeyPurpose.SIGN),
    val ecCurve: EcCurve = EcCurve.P256
)