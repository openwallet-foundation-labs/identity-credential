package org.multipaz.securearea.cloud

import org.multipaz.crypto.EcCurve
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.KeyPurpose
import org.multipaz.securearea.config.SecureAreaConfigurationCloud
import kotlinx.datetime.Instant

/**
 * Holds cloud-specific settings related to key creation.
 */
class CloudCreateKeySettings private constructor(
    keyPurposes: Set<KeyPurpose>,
    ecCurve: EcCurve,

    /**
     * Attestation challenge.
     */
    val attestationChallenge: ByteArray,

    /**
     * Whether user authentication is required.
     */
    val userAuthenticationRequired: Boolean,

    /**
     * User authentication types permitted.
     */
    val userAuthenticationTypes: Set<CloudUserAuthType>,

    /**
     * Point in time before which the key is not valid, if available.
     */
    val validFrom: Instant?,

    /**
     * Point in time after which the key is not valid, if available.
     */
    val validUntil: Instant?,

    /**
     * Whether the key is protected by a passphrase.
     */
    val passphraseRequired: Boolean,
) : CreateKeySettings(keyPurposes, ecCurve) {

    /**
     * A builder for [CloudCreateKeySettings].
     *
     * @param attestationChallenge challenge to include in attestation for the key.
     */
    class Builder(private val attestationChallenge: ByteArray) {
        private var keyPurposes = setOf(KeyPurpose.SIGN)
        private var ecCurve = EcCurve.P256
        private var userAuthenticationRequired = false
        private var userAuthenticationTypes = setOf<CloudUserAuthType>()
        private var validFrom: Instant? = null
        private var validUntil: Instant? = null
        private var passphraseRequired = false

        /**
         * Apply settings from configuration object.
         *
         * @param configuration configuration from a CBOR map.
         * @return the builder.
         */
        fun applyConfiguration(configuration: SecureAreaConfigurationCloud) = apply {
            setKeyPurposes(KeyPurpose.decodeSet(configuration.purposes))
            setEcCurve(EcCurve.fromInt(configuration.curve))
            setPassphraseRequired(configuration.passphraseRequired)
            setUserAuthenticationRequired(
                configuration.userAuthenticationRequired,
                CloudUserAuthType.decodeSet(configuration.userAuthenticationTypes)
            )
        }

        /**
         * Sets the key purpose.
         *
         * By default the key purpose is [KeyPurpose.SIGN].
         *
         * @param keyPurposes one or more purposes.
         * @return the builder.
         * @throws IllegalArgumentException if no purpose is set.
         */
        fun setKeyPurposes(keyPurposes: Set<KeyPurpose>) = apply {
            require(!keyPurposes.isEmpty()) { "Purpose cannot be empty" }
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
         * Specify if user authentication is required to use the key.
         *
         * By default, no user authentication is required.
         *
         * @param required True if user authentication is required, false otherwise.
         * @param types a combination of the flags [CloudUserAuthType.PASSCODE]
         *     and [CloudSecureAreaUserAuthType.BIOMETRIC]. Cannot be empty if [required] is `true`.
         * @return the builder.
         */
        fun setUserAuthenticationRequired(
            required: Boolean,
            types: Set<CloudUserAuthType>
        ) = apply {
            userAuthenticationRequired = required
            if (userAuthenticationRequired) {
                userAuthenticationTypes = types
                check(!userAuthenticationTypes.isEmpty()) {
                    "userAuthenticationTypes cannot be empty if user authentication is required"
                }
            } else {
                userAuthenticationTypes = emptySet()
            }
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
        fun setValidityPeriod(
            validFrom: Instant,
            validUntil: Instant
        ) = apply {
            this.validFrom = validFrom
            this.validUntil = validUntil
        }

        /**
         * Sets whether the wallet passphrase is required to use the key.
         *
         * @param required whether the wallet passphrase is required.
         */
        fun setPassphraseRequired(required: Boolean) = apply {
            passphraseRequired = required
        }

        /**
         * Builds the [CreateKeySettings].
         *
         * @return a new [CreateKeySettings].
         */
        fun build(): CloudCreateKeySettings {
            return CloudCreateKeySettings(
                keyPurposes,
                ecCurve,
                attestationChallenge,
                userAuthenticationRequired,
                userAuthenticationTypes,
                validFrom,
                validUntil,
                passphraseRequired,
            )
        }
    }
}