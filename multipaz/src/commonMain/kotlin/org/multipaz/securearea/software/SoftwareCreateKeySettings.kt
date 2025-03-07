package org.multipaz.securearea.software

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcCurve
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.CreateKeySettings.Companion.defaultSigningAlgorithm
import org.multipaz.securearea.KeyPurpose
import org.multipaz.securearea.PassphraseConstraints
import org.multipaz.securearea.config.SecureAreaConfigurationSoftware
import kotlinx.datetime.Instant;

/**
 * Class used to indicate key creation settings for software-backed keys.
 */
class SoftwareCreateKeySettings internal constructor(
    val passphraseRequired: Boolean,
    val passphrase: String?,
    val passphraseConstraints: PassphraseConstraints?,
    ecCurve: EcCurve,
    keyPurposes: Set<KeyPurpose>,
    signingAlgorithm: Algorithm,
    val subject: String?,
    val validFrom: Instant?,
    val validUntil: Instant?
) : CreateKeySettings(
    keyPurposes,
    ecCurve,
    signingAlgorithm
) {
    /**
     * A builder for [SoftwareCreateKeySettings].
     */
    class Builder {
        private var keyPurposes: Set<KeyPurpose> = setOf(KeyPurpose.SIGN)
        private var ecCurve: EcCurve = EcCurve.P256
        private var signingAlgorithm: Algorithm = Algorithm.UNSET
        private var passphraseRequired: Boolean = false
        private var passphrase: String? = null
        private var passphraseConstraints: PassphraseConstraints? = null
        private var subject: String? = null
        private var validFrom: Instant? = null
        private var validUntil: Instant? = null

        /**
         * Apply settings from configuration object.
         *
         * @param configuration configuration from a CBOR map.
         * @return the builder.
         */
        fun applyConfiguration(configuration: SecureAreaConfigurationSoftware) = apply {
            setKeyPurposes(KeyPurpose.decodeSet(configuration.purposes))
            setEcCurve(EcCurve.fromInt(configuration.curve))
            setPassphraseRequired(
                required = configuration.passphrase != null,
                passphrase = configuration.passphrase,
                constraints = configuration.passphraseConstraints
            )
        }

        /**
         * Sets the key purposes.
         *
         * By default the key purpose is [KeyPurpose.SIGN].
         *
         * @param keyPurposes one or more purposes.
         * @return the builder.
         * @throws IllegalArgumentException if no purpose is set.
         */
        fun setKeyPurposes(keyPurposes: Set<KeyPurpose>) = apply {
            require(keyPurposes.isNotEmpty()) { "Purposes cannot be empty" }
            this.keyPurposes = keyPurposes
        }

        /**
         * Sets the curve to use for EC keys.
         *
         * By default [EcCurve.P256] is used.
         *
         * @param curve the curve to use.
         * @return the builder.
         */
        fun setEcCurve(curve: EcCurve) = apply {
            ecCurve = curve
        }

        /**
         * Sets the signing algorithm.
         *
         * This is only relevant if [KeyPurpose.SIGN] is used. If unset, an appropriate
         * default is selected using [defaultSigningAlgorithm].
         */
        fun setSigningAlgorithm(algorithm: Algorithm): Builder {
            this.signingAlgorithm = algorithm
            return this
        }

        /**
         * Sets the passphrase required to use a key.
         *
         * @param required whether a passphrase is required.
         * @param passphrase the passphrase to use, must not be `null` if `required` is `true`.
         * @param constraints constraints for the passphrase or `null` if not constrained.
         * @return the builder.
         */
        fun setPassphraseRequired(
            required: Boolean,
            passphrase: String?,
            constraints: PassphraseConstraints?
        ) = apply {
            check(!passphraseRequired || passphrase != null) {
                "Passphrase cannot be null if passphrase is required"
            }
            passphraseRequired = required
            this.passphrase = passphrase
            this.passphraseConstraints = constraints
        }

        /**
         * Sets the subject of the key, to be included in the attestation.
         *
         * @param subject subject field
         * @return the builder.
         */
        fun setSubject(subject: String?) = apply {
            this.subject = subject
        }

        /**
         * Sets the key validity period.
         *
         * By default the key validity period is unbounded.
         *
         * @param validFrom the point in time before which the key is not valid.
         * @param validUntil the point in time after which the key is not valid.
         * @return the builder.
         */
        fun setValidityPeriod(validFrom: Instant, validUntil: Instant) = apply {
            this.validFrom = validFrom
            this.validUntil = validUntil
        }

        /**
         * Builds the [SoftwareCreateKeySettings].
         *
         * @return a new [SoftwareCreateKeySettings].
         */
        fun build(): SoftwareCreateKeySettings {
            if (keyPurposes.contains(KeyPurpose.SIGN) && signingAlgorithm == Algorithm.UNSET) {
                signingAlgorithm = defaultSigningAlgorithm(keyPurposes, ecCurve)
            }
            return SoftwareCreateKeySettings(
                passphraseRequired, passphrase, passphraseConstraints, ecCurve,
                keyPurposes, signingAlgorithm, subject, validFrom, validUntil
            )
        }
    }
}