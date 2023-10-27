package com.android.identity.android.securearea

import android.os.Build
import com.android.identity.crypto.EcCurve
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.util.Timestamp

/**
 * Holds cloud-specific settings related to key creation.
 */
class CloudCreateKeySettings private constructor(
    keyPurposes: Set<KeyPurpose>,
    ecCurve: EcCurve,
    attestationChallenge: ByteArray,

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
    val validFrom: Timestamp?,

    /**
     * Point in time after which the key is not valid, if available.
     */
    val validUntil: Timestamp?,

    /**
     * Whether the key is protected by a passphrase.
     */
    val passphraseRequired: Boolean,

    passphrase: String?,
    useStrongBox: Boolean

) : CreateKeySettings(attestationChallenge, keyPurposes, ecCurve) {

    /**
     * The passphrase for the key, if set.
     */
    val passphrase: String?

    /**
     * Whether StrongBox is used for the local key.
     */
    val useStrongBox: Boolean

    init {
        this.passphrase = passphrase
        this.useStrongBox = useStrongBox
    }

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
        private var validFrom: Timestamp? = null
        private var validUntil: Timestamp? = null
        private var passphraseRequired = false
        private var passphrase: String? = null
        private var useStrongBox = false

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
            validFrom: Timestamp,
            validUntil: Timestamp
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
         * Sets the passphrase required to use a key.
         *
         * @param required whether a passphrase is required.
         * @param passphrase the passphrase to use, must not be `null` if `required` is `true`.
         * @return the builder.
         */
        fun setPassphraseRequired(
            required: Boolean,
            passphrase: String?
        ) = apply {
            check(!(passphraseRequired && passphrase == null)) {
                "Passphrase cannot be null if it's required"
            }
            passphraseRequired = required
            this.passphrase = passphrase
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
                passphrase,
                useStrongBox
            )
        }
    }
}