package org.multipaz.securearea

/**
 * An enumeration for different user authentication types.
 */
enum class UserAuthenticationType(val flagValue: Long) {
    /**
     * Flag indicating that authentication is needed using the user's Lock-Screen Knowledge
     * Factor (PIN, passphrase, or pattern)
     */
    LSKF(1 shl 0),

    /**
     * Flag indicating that authentication is needed using the user's biometric.
     */
    BIOMETRIC(1 shl 1);

    companion object {
        /**
         * Helper to encode a set of [UserAuthenticationType] as an integer.
         */
        fun encodeSet(types: Set<UserAuthenticationType>): Long {
            var value = 0L
            for (type in types) {
                value = value or type.flagValue
            }
            return value
        }

        /**
         * Helper to decode an integer into a set of [UserAuthenticationType].
         */
        fun decodeSet(types: Long): Set<UserAuthenticationType> {
            val result = mutableSetOf<UserAuthenticationType>()
            for (type in UserAuthenticationType.values()) {
                if ((types and type.flagValue) != 0L) {
                    result.add(type)
                }
            }
            return result
        }
    }
}

/** Decodes the number into a set of [UserAuthenticationType] */
val Long.userAuthenticationTypeSet: Set<UserAuthenticationType>
    get() = UserAuthenticationType.decodeSet(this)
