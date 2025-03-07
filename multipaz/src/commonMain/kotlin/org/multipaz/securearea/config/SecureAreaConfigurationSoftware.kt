package org.multipaz.securearea.config

import org.multipaz.crypto.Algorithm
import org.multipaz.securearea.PassphraseConstraints
import org.multipaz.securearea.software.SoftwareSecureArea

/**
 * Configuration for [SoftwareSecureArea]
 */
class SecureAreaConfigurationSoftware(
    algorithm: String = Algorithm.ESP256.name,
    val passphrase: String? = null,
    val passphraseConstraints: PassphraseConstraints = PassphraseConstraints.NONE
): SecureAreaConfiguration(algorithm)