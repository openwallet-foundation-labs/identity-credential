package org.multipaz.securearea.config

import org.multipaz.crypto.EcCurve
import org.multipaz.securearea.KeyPurpose
import org.multipaz.securearea.PassphraseConstraints
import org.multipaz.securearea.software.SoftwareSecureArea

/**
 * Configuration for [SoftwareSecureArea]
 */
class SecureAreaConfigurationSoftware(
    purposes: Long = KeyPurpose.encodeSet(setOf(KeyPurpose.SIGN)),
    curve: Int = EcCurve.P256.coseCurveIdentifier,
    val passphrase: String? = null,
    val passphraseConstraints: PassphraseConstraints = PassphraseConstraints.NONE
): SecureAreaConfiguration(purposes, curve)