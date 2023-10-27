package com.android.identity.android.securearea.cloud

import android.os.Build
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.cbor.DataItem
import com.android.identity.crypto.EcCurve
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
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
     * User authentication timeout, if any.
     */
    val userAuthenticationTimeoutMillis: Long,

    /**
     * User authentication type.
     */
    val userAuthenticationType: Set<UserAuthenticationType>,

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

    /**
     * Whether StrongBox is used for the local key.
     */
    val useStrongBox: Boolean

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
        private var userAuthenticationTimeoutMillis: Long = 0
        private var userAuthenticationTypes = setOf<UserAuthenticationType>()
        private var validFrom: Instant? = null
        private var validUntil: Instant? = null
        private var passphraseRequired = false
        private var useStrongBox = false

        /**
         * Apply settings from configuration object.
         *
         * @param configuration configuration from a CBOR map.
         * @return the builder.
         */
        fun applyConfiguration(configuration: DataItem) = apply {
            var userAutenticationRequired = false
            var userAuthenticationTimeoutMillis = 0L
            var userAuthenticationTypes = setOf<UserAuthenticationType>()
            for ((key, value) in configuration.asMap) {
                when (key.asTstr) {
                    "purposes" -> setKeyPurposes(KeyPurpose.decodeSet(value.asNumber))
                    "curve" -> setEcCurve(EcCurve.fromInt(value.asNumber.toInt()))
                    "useStrongBox" -> setUseStrongBox(value.asBoolean)
                    "userAuthenticationRequired" -> userAutenticationRequired = value.asBoolean
                    "userAuthenticationTimeoutMillis" -> userAuthenticationTimeoutMillis = value.asNumber
                    "userAuthenticationTypes" -> userAuthenticationTypes = UserAuthenticationType.decodeSet(value.asNumber)
                    "passphraseRequired" -> setPassphraseRequired(value.asBoolean)
                }
            }
            setUserAuthenticationRequired(
                userAutenticationRequired,
                userAuthenticationTimeoutMillis,
                userAuthenticationTypes
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
         * On devices with prior to API 30, `userAuthenticationType` must be
         * [UserAuthenticationType.LSKF] combined with [UserAuthenticationType.BIOMETRIC].
         * On API 30 and later either flag may be used independently. The value cannot
         * be empty if user authentication is required.
         *
         * By default, no user authentication is required.
         *
         * @param required True if user authentication is required, false otherwise.
         * @param timeoutMillis If 0, user authentication is required for every use of
         * the key, otherwise it's required within the given amount of milliseconds.
         * @param userAuthenticationTypes a combination of the flags
         * [UserAuthenticationType.LSKF] and [UserAuthenticationType.BIOMETRIC].
         * @return the builder.
         */
        fun setUserAuthenticationRequired(
            required: Boolean,
            timeoutMillis: Long,
            userAuthenticationTypes: Set<UserAuthenticationType>
        ) = apply {
            if (required) {
                require(!userAuthenticationTypes.isEmpty()) {
                    "userAuthenticationType must be set when user authentication is required"
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    require(
                        userAuthenticationTypes === setOf(
                            UserAuthenticationType.LSKF,
                            UserAuthenticationType.BIOMETRIC
                        )
                    ) { "Only LSKF and Strong Biometric supported on this API level" }
                }
            }
            userAuthenticationRequired = required
            userAuthenticationTimeoutMillis = timeoutMillis
            this.userAuthenticationTypes = userAuthenticationTypes
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
         * Specify if StrongBox Android Keystore should be used for the local key, if available.
         *
         * By default StrongBox isn't used.
         *
         * @param useStrongBox Whether to use StrongBox for the local key.
         * @return the builder.
         */
        fun setUseStrongBox(useStrongBox: Boolean) = apply {
            this.useStrongBox = useStrongBox
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
                userAuthenticationTimeoutMillis,
                userAuthenticationTypes,
                validFrom,
                validUntil,
                passphraseRequired,
                useStrongBox,
            )
        }
    }
}