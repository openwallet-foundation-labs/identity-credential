package org.multipaz.securearea

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.crypto.Algorithm


/**
 * Class for holding Secure Enclave settings related to key creation.
 */
class SecureEnclaveCreateKeySettings private constructor(
    algorithm: Algorithm,

    userAuthenticationRequired: Boolean,

    /**
     * The user authentication types that can be used to unlock the key.
     */
    val userAuthenticationTypes: Set<SecureEnclaveUserAuthType>

) : CreateKeySettings(algorithm, ByteString(), userAuthenticationRequired) {

    /**
     * A builder for [SecureEnclaveCreateKeySettings].
     */
    class Builder {
        private var algorithm: Algorithm = Algorithm.ESP256
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
                    "algorithm" -> setAlgorithm(Algorithm.fromName(value.asTstr))
                    "userAuthenticationRequired" -> userAutenticationRequired = value.asBoolean
                    "userAuthenticationTypes" -> userAuthenticationTypes =
                        SecureEnclaveUserAuthType.decodeSet(value.asNumber)
                }
            }
            setUserAuthenticationRequired(userAutenticationRequired, userAuthenticationTypes)
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
                algorithm,
                userAuthenticationRequired,
                userAuthenticationTypes
            )
        }
    }
}