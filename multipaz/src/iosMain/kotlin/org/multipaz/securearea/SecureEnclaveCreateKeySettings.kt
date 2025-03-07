package org.multipaz.securearea

import org.multipaz.cbor.DataItem
import org.multipaz.crypto.EcCurve


/**
 * Class for holding Secure Enclave settings related to key creation.
 */
class SecureEnclaveCreateKeySettings private constructor(

    keyPurposes: Set<KeyPurpose>,

    /**
     * Whether user authentication is required.
     */
    val userAuthenticationRequired: Boolean,

    /**
     * The user authentication types that can be used to unlock the key.
     */
    val userAuthenticationTypes: Set<SecureEnclaveUserAuthType>

) : CreateKeySettings(keyPurposes, EcCurve.P256) {

    /**
     * A builder for [SecureEnclaveCreateKeySettings].
     */
    class Builder {
        private var keyPurposes = setOf(KeyPurpose.SIGN)
        private var userAuthenticationRequired = false
        private var userAuthenticationTypes = setOf<SecureEnclaveUserAuthType>()

        /**
         * Apply settings from configuration object.
         *
         * @param configuration configuration from a CBOR map.
         * @return the builder.
         */
        fun applyConfiguration(configuration: DataItem) = apply {
            var userAutenticationRequired = false
            var userAuthenticationTypes = setOf<SecureEnclaveUserAuthType>()
            for ((key, value) in configuration.asMap) {
                when (key.asTstr) {
                    "purposes" -> setKeyPurposes(KeyPurpose.decodeSet(value.asNumber))
                    "userAuthenticationRequired" -> userAutenticationRequired = value.asBoolean
                    "userAuthenticationTypes" -> userAuthenticationTypes =
                        SecureEnclaveUserAuthType.decodeSet(value.asNumber)
                }
            }
            setUserAuthenticationRequired(userAutenticationRequired, userAuthenticationTypes)
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
         * Method to specify if user authentication is required to use the key.
         *
         * By default, no user authentication is required.
         *
         * @param required True if user authentication is required, false otherwise.
         * @param userAuthenticationTypes a combination of [SecureEnclaveUserAuthType] flags.
         */
        fun setUserAuthenticationRequired(
            required: Boolean,
            userAuthenticationTypes: Set<SecureEnclaveUserAuthType>
        ): Builder {
            userAuthenticationRequired = required
            this.userAuthenticationTypes = userAuthenticationTypes
            return this
        }

        /**
         * Builds the [CreateKeySettings].
         *
         * @return a new [CreateKeySettings].
         */
        fun build(): SecureEnclaveCreateKeySettings {
            return SecureEnclaveCreateKeySettings(
                keyPurposes,
                userAuthenticationRequired,
                userAuthenticationTypes
            )
        }
    }
}