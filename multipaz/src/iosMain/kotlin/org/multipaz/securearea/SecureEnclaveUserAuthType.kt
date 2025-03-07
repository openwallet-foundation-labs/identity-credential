package org.multipaz.securearea

/**
 * An enumeration for different user authentication types when using keys in the Secure Enclave.
 */
enum class SecureEnclaveUserAuthType(val flagValue: Long) {
    /**
     * Flag indicating that authentication is needed using the device passcode.
     */
    DEVICE_PASSCODE(1 shl 0),

    /**
     * Flag indicating that authentication is needed using the current user's biometric.
     */
    BIOMETRY_CURRENT_SET(1 shl 1),

    /**
     * Flag indicating that authentication is needed using any user's biometric.
     */
    BIOMETRY_ANY(1 shl 2),

    /**
     * Flag indicating that authentication is needed using any user's biometric or the device
     * passcode.
     */
    USER_PRESENCE(1 shl 3);

    companion object {
        /**
         * Helper to encode a set of [UserAuthenticationType] as an integer.
         */
        fun encodeSet(types: Set<SecureEnclaveUserAuthType>): Long {
            var value = 0L
            for (type in types) {
                value = value or type.flagValue
            }
            return value
        }

        /**
         * Helper to decode an integer into a set of [UserAuthenticationType].
         */
        fun decodeSet(types: Long): Set<SecureEnclaveUserAuthType> {
            val result = mutableSetOf<SecureEnclaveUserAuthType>()
            for (type in SecureEnclaveUserAuthType.values()) {
                if ((types and type.flagValue) != 0L) {
                    result.add(type)
                }
            }
            return result
        }
    }
}

/** Decodes the number into a set of [UserAuthenticationType] */
val Long.secureEnclaveUserAuthType: Set<SecureEnclaveUserAuthType>
    get() = SecureEnclaveUserAuthType.decodeSet(this)
