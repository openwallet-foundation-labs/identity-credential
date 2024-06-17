package com.android.identity.android.securearea

import android.os.Build
import com.android.identity.cbor.DataItem
import com.android.identity.crypto.EcCurve
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import kotlinx.datetime.Instant

/**
 * Class for holding Android Keystore-specific settings related to key creation.
 */
class AndroidKeystoreCreateKeySettings private constructor(

    keyPurposes: Set<KeyPurpose>,

    ecCurve: EcCurve,

    /**
     * The attestation challenge.
     */
    val attestationChallenge: ByteArray,

    /**
     * Gets whether user authentication is required.
     */
    val userAuthenticationRequired: Boolean,

    /**
     * The user authentication timeout, or 0 if authentication is required on every use.
     */
    val userAuthenticationTimeoutMillis: Long,

    /**
     * User authentication types for the key.
     *
     * @return a set of [UserAuthenticationType]
     */
    val userAuthenticationTypes: Set<UserAuthenticationType>,

    /**
     * Whether StrongBox is used.
     */
    val useStrongBox: Boolean,

    /**
     * The attest key alias, if any.
     */
    val attestKeyAlias: String?,

    /**
     * The point in time before which the key is not valid, if set.
     */
    val validFrom: Instant?,

    /**
     * The point in time after which the key is not valid, if set.
     */
    val validUntil: Instant?

) : CreateKeySettings(keyPurposes, ecCurve) {

    /**
     * A builder for [CreateKeySettings].
     *
     * @param attestationChallenge challenge to include in attestation for the key.
     */
    class Builder(private val attestationChallenge: ByteArray) {
        private var keyPurposes = setOf(KeyPurpose.SIGN)
        private var curve = EcCurve.P256
        private var userAuthenticationRequired = false
        private var userAuthenticationTimeoutMillis: Long = 0

        private var userAuthenticationTypes = emptySet<UserAuthenticationType>()
        private var useStrongBox = false
        private var attestKeyAlias: String? = null
        private var validFrom: Instant? = null
        private var validUntil: Instant? = null

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
        fun setKeyPurposes(keyPurposes: Set<KeyPurpose>): Builder {
            require(!keyPurposes.isEmpty()) { "Purposes cannot be empty" }
            this.keyPurposes = keyPurposes
            return this
        }

        /**
         * Sets the curve to use for EC keys.
         *
         * By default [EcCurve.P256] is used.
         *
         * @param curve the curve to use.
         * @return the builder.
         */
        fun setEcCurve(curve: EcCurve): Builder {
            this.curve = curve
            return this
        }

        /**
         * Method to specify if user authentication is required to use the key.
         *
         * On devices with prior to API 30, `userAuthenticationType` must be
         * [.USER_AUTHENTICATION_TYPE_LSKF] combined with
         * [.USER_AUTHENTICATION_TYPE_BIOMETRIC]. On API 30 and later
         * either flag may be used independently. The value cannot be zero if user
         * authentication is required.
         *
         * By default, no user authentication is required.
         *
         * @param required True if user authentication is required, false otherwise.
         * @param timeoutMillis If 0, user authentication is required for every use of
         * the key, otherwise it's required within the given amount
         * of milliseconds.
         * @param userAuthenticationTypes a combination of [UserAuthenticationType] flags.
         * @return the builder.
         */
        fun setUserAuthenticationRequired(
            required: Boolean,
            timeoutMillis: Long,
            userAuthenticationTypes: Set<UserAuthenticationType>
        ): Builder {
            if (required) {
                require(UserAuthenticationType.encodeSet(userAuthenticationTypes) != 0L) {
                    "userAuthenticationType must be set when user authentication is required" }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    require(UserAuthenticationType.encodeSet(userAuthenticationTypes) ==
                            UserAuthenticationType.LSKF.flagValue or
                            UserAuthenticationType.BIOMETRIC.flagValue) {
                        "Only LSKF and Strong Biometric supported on this API level" }
                }
            }
            userAuthenticationRequired = required
            userAuthenticationTimeoutMillis = timeoutMillis
            this.userAuthenticationTypes = userAuthenticationTypes
            return this
        }

        /**
         * Method to specify if StrongBox Android Keystore should be used, if available.
         *
         * By default StrongBox isn't used.
         *
         * @param useStrongBox Whether to use StrongBox.
         * @return the builder.
         */
        fun setUseStrongBox(useStrongBox: Boolean): Builder {
            this.useStrongBox = useStrongBox
            return this
        }

        /**
         * Method to specify if an attest key should be used.
         *
         * By default no attest key is used. See
         * [setAttestKeyAlias() method](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setAttestKeyAlias(java.lang.String))
         * for more information about attest keys.
         *
         * @param attestKeyAlias the Android Keystore alias of the attest key or `null` to not use an attest key.
         * @return the builder.
         */
        fun setAttestKeyAlias(attestKeyAlias: String?): Builder {
            this.attestKeyAlias = attestKeyAlias
            return this
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
        ): Builder {
            this.validFrom = validFrom
            this.validUntil = validUntil
            return this
        }

        /**
         * Builds the [CreateKeySettings].
         *
         * @return a new [CreateKeySettings].
         */
        fun build(): AndroidKeystoreCreateKeySettings {
            return AndroidKeystoreCreateKeySettings(
                keyPurposes,
                curve,
                attestationChallenge,
                userAuthenticationRequired,
                userAuthenticationTimeoutMillis,
                userAuthenticationTypes,
                useStrongBox,
                attestKeyAlias,
                validFrom,
                validUntil
            )
        }
    }
}