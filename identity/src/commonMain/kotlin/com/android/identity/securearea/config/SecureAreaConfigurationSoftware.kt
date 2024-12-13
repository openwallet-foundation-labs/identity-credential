package com.android.identity.securearea.config

import com.android.identity.crypto.EcCurve
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.securearea.software.SoftwareSecureArea

/**
 * Configuration for [SoftwareSecureArea]
 */
class SecureAreaConfigurationSoftware(
    purposes: Long = KeyPurpose.encodeSet(setOf(KeyPurpose.SIGN)),
    curve: Int = EcCurve.P256.coseCurveIdentifier,
    val passphrase: String? = null,
    val passphraseConstraints: PassphraseConstraints = PassphraseConstraints.NONE
): SecureAreaConfiguration(purposes, curve)