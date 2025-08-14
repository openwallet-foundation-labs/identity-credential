package org.multipaz.securearea.cloud

import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.config.SecureAreaConfigurationCloud
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.Algorithm
import kotlin.time.Duration.Companion.seconds

/**
 * Holds cloud-specific settings related to key creation.
 */
class CloudCreateKeySettings private constructor(
    algorithm: Algorithm,

    /**
     * Attestation challenge.
     */
    val attestationChallenge: ByteString,

    userAuthenticationRequired: Boolean,

    /**
     * User authentication types permitted.
     */
    val userAuthenticationTypes: Set<CloudUserAuthType>,

    validFrom: Instant?,

    validUntil: Instant?,

    /**
     * Whether the key is protected by a passphrase.
     */
    val passphraseRequired: Boolean,
) : CreateKeySettings(algorithm, attestationChallenge, userAuthenticationRequired, 0.seconds, validFrom, validUntil) {

    /**
     * A builder for [CloudCreateKeySettings].
     *
     * @param attestationChallenge challenge to include in attestation for the key.
     */
    class Builder(private val attestationChallenge: ByteString) {
        private var algorithm = Algorithm.ESP256
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
            setAlgorithm(Algorithm.fromName(configuration.algorithm))
            setPassphraseRequired(configuration.passphraseRequired)
            setUserAuthenticationRequired(
                configuration.userAuthenticationRequired,
                CloudUserAuthType.decodeSet(configuration.userAuthenticationTypes)
            )
        }


        /**
         * Sets the algorithm for the key.
         *
         * By default [Algorithm.ESP256] is used.
         *
         * @param algorithm a fully specified algorithm.
         * @return the builder.
         */
        fun setAlgorithm(algorithm: Algorithm) = apply {
            this.algorithm = algorithm
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
                algorithm,
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