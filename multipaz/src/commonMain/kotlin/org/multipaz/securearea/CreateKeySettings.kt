package org.multipaz.securearea

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcCurve

/**
 * Base class for key creation settings.
 *
 * This can be used for any conforming [SecureArea] implementations.
 * although such implementations will typically supply their own implementations
 * with additional settings to e.g. configure user authentication, passphrase
 * protections, challenges for attestations, and other things.
 *
 * @param keyPurposes the key purposes.
 * @param ecCurve the curve used.
 * @param signingAlgorithm algorithm to utilize when this key is used for signing,
 *    only meaningful when [keyPurposes] contains [KeyPurpose.SIGN].
 */
open class CreateKeySettings(
    val keyPurposes: Set<KeyPurpose> = setOf(KeyPurpose.SIGN),
    val ecCurve: EcCurve = EcCurve.P256,
    val signingAlgorithm: Algorithm = defaultSigningAlgorithm(keyPurposes, ecCurve)
) {
    companion object {
        /**
         * Returns the most appropriate value for [signingAlgorithm] given specified
         * [keyPurposes] and [ecCurve].
         * 
         * See also [EcCurve.defaultSigningAlgorithm].
         */
        fun defaultSigningAlgorithm(keyPurposes: Set<KeyPurpose>, ecCurve: EcCurve): Algorithm {
            return if (keyPurposes.contains(KeyPurpose.SIGN)) {
                ecCurve.defaultSigningAlgorithm
            } else {
                Algorithm.UNSET
            }
        }
    }
}